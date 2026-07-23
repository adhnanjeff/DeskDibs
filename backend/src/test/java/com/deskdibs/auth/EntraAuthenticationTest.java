package com.deskdibs.auth;

import com.deskdibs.booking.BookingRepository;
import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Entra provider: everything that can be proved without a tenant.
 *
 * <p>The app registration does not exist yet, so this context runs with placeholder identifiers.
 * That is enough to boot — {@code NimbusJwtDecoder.withJwkSetUri} fetches keys lazily — and enough
 * to test the half of the provider that is DeskDibs' own logic: which claims are read, which are
 * ignored, and what happens to the {@code app_user} row.
 *
 * <p>Identity resolution is driven with a synthetic {@link Jwt}: an object representing a token
 * that has <em>already passed</em> signature, expiry, issuer and audience validation, which is
 * exactly the input {@link EntraAuthProvider} receives in production. Building one skips the
 * network without skipping any of the logic under test.
 *
 * <p><strong>What this cannot cover.</strong> The JWKS fetch, RS256 verification against a real
 * Microsoft key, key rotation, and the issuer and audience values a live tenant actually mints.
 * Those need the app registration and are unverified until it exists.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "deskdibs.auth.provider=entra",
        // Placeholders. Never contacted: no test here presents a token to the decoder.
        "deskdibs.auth.entra.tenant-id=00000000-0000-0000-0000-0000000000aa",
        "deskdibs.auth.entra.client-id=00000000-0000-0000-0000-0000000000bb"
})
class EntraAuthenticationTest extends AbstractPostgresIntegrationTest {

    private static final String OID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final String OTHER_OID = "9c858901-8a57-4791-81fe-4c455b099bc9";
    private static final String EMAIL = "priya@contoso.com";

    private final EntraAuthProvider provider;
    private final EntraUserProvisioner provisioner;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final MockMvc mockMvc;
    private final ApplicationContext context;

    EntraAuthenticationTest(EntraAuthProvider provider,
                            EntraUserProvisioner provisioner,
                            AppUserRepository users,
                            BookingRepository bookings,
                            MockMvc mockMvc,
                            ApplicationContext context) {
        this.provider = provider;
        this.provisioner = provisioner;
        this.users = users;
        this.bookings = bookings;
        this.mockMvc = mockMvc;
        this.context = context;
    }

    @BeforeEach
    void emptyTheDirectory() {
        clearPeople();
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearPeople();
    }

    // ─── 8. Just-in-time provisioning ────────────────────────────────────────────

    @Test
    @DisplayName("a first sign-in creates the user, and the second reuses the same row")
    void aFirstSignInProvisionsAndTheSecondReuses() {
        AuthenticatedUser first = provider.resolve(entraToken(claims -> {
        }));

        assertThat(users.count()).isEqualTo(1);
        AppUser provisioned = users.findByExternalId(OID).orElseThrow();
        assertThat(provisioned.getEmail()).isEqualTo(EMAIL);
        assertThat(provisioned.getDisplayName()).isEqualTo("Priya Raman");
        assertThat(provisioned.getRole()).isEqualTo(UserRole.EMPLOYEE);
        assertThat(provisioned.isActive()).isTrue();
        assertThat(provisioned.getPasswordHash())
                .as("an SSO account never holds a password in this database")
                .isNull();
        assertThat(first.id()).isEqualTo(provisioned.getId());

        AuthenticatedUser second = provider.resolve(entraToken(claims -> {
        }));

        assertThat(users.count())
                .as("a second sign-in is not a second account")
                .isEqualTo(1);
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    @DisplayName("a token asserting admin roles and groups still provisions a plain EMPLOYEE")
    void aTokenAssertingAdminStillProvisionsAnEmployee() {
        AuthenticatedUser provisioned = provider.resolve(entraToken(claims -> {
            claims.claim("roles", List.of("ADMIN", "GlobalAdministrator"));
            claims.claim("groups", List.of("deskdibs-admins"));
            claims.claim("wids", List.of("62e90394-69f5-4237-9190-012177145e10"));
        }));

        // If a token could name the role of the row it creates, anyone who could get a token
        // could mint themselves an administrator.
        assertThat(provisioned.role()).isEqualTo(UserRole.EMPLOYEE);
        assertThat(users.findByExternalId(OID).orElseThrow().getRole()).isEqualTo(UserRole.EMPLOYEE);
    }

    @Test
    @DisplayName("an account that predates SSO is adopted by email, keeping its id and its role")
    void anAccountThatPredatesSsoIsAdoptedRatherThanDuplicated() {
        AppUser preExisting = users.saveAndFlush(new AppUser(EMAIL, "Priya R.", UserRole.MANAGER));

        AuthenticatedUser resolved = provider.resolve(entraToken(claims -> {
        }));

        assertThat(users.count()).isEqualTo(1);
        assertThat(resolved.id()).isEqualTo(preExisting.getId());
        assertThat(resolved.role())
                .as("adoption must not demote somebody who was already a manager")
                .isEqualTo(UserRole.MANAGER);
        assertThat(users.findById(preExisting.getId()).orElseThrow().getExternalId()).isEqualTo(OID);
    }

    @Test
    @DisplayName("an email already owned by a different object id is refused, not reassigned")
    void anEmailOwnedByADifferentObjectIdIsRefused() {
        AppUser incumbent = users.saveAndFlush(new AppUser(EMAIL, "Priya R.", UserRole.EMPLOYEE));
        incumbent.setExternalId(OTHER_OID);
        users.saveAndFlush(incumbent);

        // A recycled mailbox. Re-pointing the row would hand the new joiner the leaver's history.
        assertThatThrownBy(() -> provider.resolve(entraToken(claims -> {
        })))
                .isInstanceOf(IdentityConflictException.class)
                .satisfies(refusal -> assertThat(((AuthException) refusal).errorCode())
                        .isEqualTo(AuthErrorCode.IDENTITY_CONFLICT));

        assertThat(users.findByExternalId(OTHER_OID)).isPresent();
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a token with no object id provisions nobody, even with an email")
    void aTokenWithNoObjectIdProvisionsNobody() {
        Jwt withoutOid = Jwt.withTokenValue("synthetic")
                .header("alg", "RS256")
                .claim("preferred_username", "someone.new@contoso.com")
                .claim("name", "Someone New")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        assertThatThrownBy(() -> provider.resolve(withoutOid))
                .isInstanceOf(UnknownPrincipalException.class);
        assertThat(users.count())
                .as("an email alone is not a stable identifier to key an account on")
                .isZero();
    }

    @Test
    @DisplayName("the display name follows the tenant, and only when it has actually changed")
    void theDisplayNameFollowsTheTenant() {
        long id = provider.resolve(entraToken(claims -> {
        })).id();

        AuthenticatedUser renamed = provider.resolve(entraToken(claims -> claims.claim("name", "Priya Raman-Iyer")));

        assertThat(renamed.id()).isEqualTo(id);
        assertThat(users.findById(id).orElseThrow().getDisplayName()).isEqualTo("Priya Raman-Iyer");
        assertThat(users.findById(id).orElseThrow().getEmail())
                .as("the email stays frozen: changing it can collide, and a filter is no place to find out")
                .isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("a deactivated user is refused even with a token the tenant would still accept")
    void aDeactivatedUserIsRefused() {
        long id = provider.resolve(entraToken(claims -> {
        })).id();
        AppUser user = users.findById(id).orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);

        assertThatThrownBy(() -> provider.resolve(entraToken(claims -> {
        })))
                .isInstanceOf(UserDeactivatedException.class);
    }

    @Test
    @DisplayName("the provisioner reads only oid, email and name off the token")
    void theProvisionerReadsOnlyTheThreeClaimsItNeeds() {
        EntraIdentity identity = EntraIdentity.from(entraToken(claims -> claims
                .claim("roles", List.of("ADMIN"))
                .claim("scp", "Booking.ReadWrite.All")));

        assertThat(identity.externalId()).isEqualTo(OID);
        assertThat(identity.email()).isEqualTo(EMAIL);
        assertThat(identity.name()).isEqualTo("Priya Raman");

        // Reached through the provisioner directly, the same way the provider reaches it.
        AppUser provisioned = provisioner.resolve(identity);
        assertThat(provisioned.getRole()).isEqualTo(UserRole.EMPLOYEE);
    }

    // ─── Wiring ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("switching the provider swaps the whole identity layer, password login included")
    void switchingTheProviderSwapsTheWholeIdentityLayer() {
        assertThat(context.getBean(AuthProvider.class).kind()).isEqualTo(AuthProviderKind.ENTRA);
        assertThat(context.getBean(JwtDecoder.class)).isNotNull();

        // Not merely disabled: absent. There is no password endpoint to reach under SSO.
        assertThat(context.getBeansOfType(LocalLoginController.class)).isEmpty();
        assertThat(context.getBeansOfType(LocalLoginService.class)).isEmpty();
        assertThat(context.getBeansOfType(DevUserSeeder.class)).isEmpty();
        assertThatThrownBy(() -> context.getBean(LocalTokenIssuer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    @DisplayName("the fail-closed default and the JSON 401 hold under Entra too")
    void theFailClosedDefaultHoldsUnderEntra() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.UNAUTHENTICATED.name()));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    /**
     * A token as it looks <em>after</em> the decoder has accepted it: signature, expiry, issuer and
     * audience are already settled, and what remains is the claim set the provider reads.
     */
    private static Jwt entraToken(Consumer<Jwt.Builder> extra) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("synthetic-validated-token")
                .header("alg", "RS256")
                .header("kid", "synthetic-key")
                .claim("oid", OID)
                .claim("preferred_username", EMAIL)
                .claim("name", "Priya Raman")
                .claim("tid", "00000000-0000-0000-0000-0000000000aa")
                .subject("pairwise-subject-that-is-not-the-oid")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS));
        extra.accept(builder);
        return builder.build();
    }

    private void clearPeople() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
    }
}
