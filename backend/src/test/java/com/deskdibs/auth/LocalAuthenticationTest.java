package com.deskdibs.auth;

import com.deskdibs.booking.BookingRepository;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The local provider, end to end through the security filter chain.
 *
 * <p>Everything here is exercised as an HTTP request against real PostgreSQL: a token is obtained
 * by logging in, presented on a protected endpoint, and refused when it should be. The point of
 * going through the chain rather than calling services is that the failures worth catching live in
 * the wiring — a matcher that lets something through, an entry point that returns HTML, a
 * converter that reads authorities from the wrong place.
 */
class LocalAuthenticationTest extends AbstractAuthWebTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final LocalTime NINE_AM = ControllableClockConfiguration.DEFAULT_TIME_OF_DAY;

    /** Tokens live 8 hours (PT8H), so 18:00 is comfortably past a token minted at 09:00. */
    private static final LocalTime AFTER_TOKEN_EXPIRY = LocalTime.of(18, 0);

    /**
     * Generated per run rather than written down. A literal password in a committed test is both
     * a secret-scanner false positive and a value somebody eventually copies somewhere real.
     */
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String WRONG_PASSWORD = UUID.randomUUID().toString();

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final LocalAuthProperties localAuth;
    private final MutableClock clock;
    private final ZoneId office;

    private long employeeId;
    private long adminId;

    LocalAuthenticationTest(MockMvc mockMvc,
                            ObjectMapper json,
                            AppUserRepository users,
                            BookingRepository bookings,
                            PasswordEncoder passwordEncoder,
                            JwtEncoder jwtEncoder,
                            LocalAuthProperties localAuth,
                            MutableClock clock,
                            OfficeProperties office) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.users = users;
        this.bookings = bookings;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.localAuth = localAuth;
        this.clock = clock;
        this.office = office.timezone();
    }

    @BeforeEach
    void createTheOfficeStaff() {
        moveClockTo(NINE_AM);
        clearPeople();

        employeeId = person("employee@deskdibs.test", "Erin Employee", UserRole.EMPLOYEE, PASSWORD);
        adminId = person("admin@deskdibs.test", "Ada Admin", UserRole.ADMIN, PASSWORD);
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearPeople();
    }

    // ─── 1. Logging in ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("logging in with the right password returns a token that opens a protected endpoint")
    void loggingInWithTheRightPasswordReturnsAUsableToken() throws Exception {
        JsonNode login = body(login("employee@deskdibs.test", PASSWORD).andExpect(status().isOk()));

        assertThat(login.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(login.path("expiresInSeconds").asLong()).isEqualTo(localAuth.tokenTtl().toSeconds());
        assertThat(login.path("user").path("id").asLong()).isEqualTo(employeeId);
        assertThat(login.path("user").path("role").asText()).isEqualTo("EMPLOYEE");

        String token = login.path("accessToken").asText();
        assertThat(token).isNotBlank();

        // The token is only proven usable by using it.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId))
                .andExpect(jsonPath("$.email").value("employee@deskdibs.test"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.provider").value("LOCAL"));
    }

    // ─── 2. No user enumeration ──────────────────────────────────────────────────

    @Test
    @DisplayName("a wrong password and an unknown email are refused with byte-identical responses")
    void aWrongPasswordAndAnUnknownEmailAreIndistinguishable() throws Exception {
        MvcResult wrongPassword = login("employee@deskdibs.test", WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownEmail = login("nobody@deskdibs.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Not merely "both are 401": the whole body. A difference in wording, in error code, or
        // even in which fields are present would turn this endpoint into a staff directory.
        assertThat(unknownEmail.getResponse().getContentAsString())
                .as("an unknown address must not be distinguishable from a wrong password")
                .isEqualTo(wrongPassword.getResponse().getContentAsString());

        JsonNode refusal = body(wrongPassword);
        assertThat(refusal.path("code").asText()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.name());
        assertThat(refusal.path("message").asText()).isEqualTo("Invalid email or password.");
    }

    @Test
    @DisplayName("a deactivated account is refused at login exactly like a wrong password")
    void aDeactivatedAccountIsRefusedAtLoginLikeAnyOtherFailure() throws Exception {
        deactivate(employeeId);

        MvcResult deactivated = login("employee@deskdibs.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult wrongPassword = login("admin@deskdibs.test", WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();

        // "This account is disabled" would announce who has just left the company.
        assertThat(deactivated.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    // ─── 3-5. What the resource server refuses ───────────────────────────────────

    @Test
    @DisplayName("no token on a protected endpoint is 401 JSON, not an HTML error page")
    void noTokenOnAProtectedEndpointIs401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                // A leaked stack trace is the classic way an error page becomes reconnaissance.
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("a token with a tampered signature is refused")
    void aTokenWithATamperedSignatureIsRefused() throws Exception {
        String token = tokenFor(employeeId);

        expectInvalidToken(tamperWithSignature(token));
    }

    @Test
    @DisplayName("rewriting the subject to the admin's id while keeping the signature is refused")
    void aTokenWhosePayloadWasRewrittenToImpersonateSomebodyElseIsRefused() throws Exception {
        // The genuine attack: a valid token the holder owns, edited to claim somebody else's
        // identity. The signature no longer covers the claims, so it must not survive.
        String mine = tokenFor(employeeId);

        expectInvalidToken(tamperWithPayload(mine, adminId));
    }

    @Test
    @DisplayName("a well-formed token signed with the wrong key is refused")
    void aWellFormedTokenSignedWithTheWrongKeyIsRefused() throws Exception {
        // Every claim is right — issuer, audience, subject, expiry. Only the key is not ours,
        // which is exactly the token an attacker who knows the format but not the secret can mint.
        JwtEncoder impostor = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(randomKey()));
        String forged = impostor.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claimsFor(employeeId).build()))
                .getTokenValue();

        expectInvalidToken(forged);
    }

    @Test
    @DisplayName("an expired token is refused once the office clock passes its lifetime")
    void anExpiredTokenIsRefused() throws Exception {
        String token = tokenFor(employeeId);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // No sleeping: the decoder validates timestamps against the application's clock bean.
        moveClockTo(AFTER_TOKEN_EXPIRY);

        expectInvalidToken(token);
    }

    // ─── 6. The one that matters most ────────────────────────────────────────────

    @Test
    @DisplayName("a token claiming ADMIN grants nothing when the database says EMPLOYEE")
    void aTokenClaimingAdminGrantsNothingWhenTheDatabaseSaysEmployee() throws Exception {
        // Correctly signed, unexpired, right issuer and audience — an authentic token in every
        // respect, and asserting three different flavours of "I am an administrator".
        String forged = signed(claims -> claims
                .subject(String.valueOf(employeeId))
                .claim("role", "ADMIN")
                .claim("roles", List.of("ADMIN", "Booking.Admin"))
                .claim("groups", List.of("deskdibs-admins"))
                .claim("scp", "admin"));

        // The claim is not read, so it cannot win.
        mockMvc.perform(get(AdminOnlyTestEndpoint.PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCESS_DENIED.name()));

        // The identity is honoured; only the assertion of privilege is ignored.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("the same endpoint opens for a token with no role claim at all when the database says ADMIN")
    void theSameEndpointOpensWhenTheDatabaseSaysAdmin() throws Exception {
        // The mirror image, and the reason the test above is not passing for a boring reason:
        // authority comes from the row, so a token that claims nothing still gets everything.
        String plain = tokenFor(adminId);

        mockMvc.perform(get(AdminOnlyTestEndpoint.PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + plain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value("true"));
    }

    @Test
    @DisplayName("demoting a user in the database revokes admin power held under an unexpired token")
    void demotingAUserRevokesAdminPowerImmediately() throws Exception {
        String token = tokenFor(adminId);
        mockMvc.perform(get(AdminOnlyTestEndpoint.PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        setRole(adminId, UserRole.EMPLOYEE);

        // Same token, same signature, same expiry. The role is re-read per request, so a demotion
        // takes effect now rather than whenever the last issued token happens to lapse.
        mockMvc.perform(get(AdminOnlyTestEndpoint.PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ─── 7. Deactivation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a deactivated user's still-valid token stops working immediately")
    void aDeactivatedUsersValidTokenIsRefused() throws Exception {
        String token = tokenFor(employeeId);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        deactivate(employeeId);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.USER_DEACTIVATED.name()));
    }

    @Test
    @DisplayName("a token whose subject no longer exists is refused rather than treated as anonymous")
    void aTokenWhoseSubjectNoLongerExistsIsRefused() throws Exception {
        String token = signed(claims -> claims.subject("999999999"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.UNKNOWN_PRINCIPAL.name()));
    }

    // ─── 9. Fail closed ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("an endpoint nobody permitted requires authentication, even before it exists")
    void anEndpointNobodyPermittedRequiresAuthentication() throws Exception {
        // Nothing is mapped here. The chain still refuses before routing, which is what makes a
        // Phase 4 controller protected the moment somebody writes it rather than when they
        // remember to annotate it.
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.UNAUTHENTICATED.name()));

        mockMvc.perform(post("/api/seats/1/claim").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the health probe is reachable without a token, so the deny is a rule and not a blanket")
    void theHealthProbeIsReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a malformed login body is rejected before any account lookup happens")
    void aMalformedLoginBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.VALIDATION_FAILED.name()));
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private void expectInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_TOKEN.name()))
                .andExpect(jsonPath("$.message").value("Invalid or expired token."));
    }

    /** A genuine token for a user, minted the way the login endpoint mints one. */
    private String tokenFor(long userId) {
        return signed(claims -> claims.subject(String.valueOf(userId)));
    }

    private String signed(Consumer<JwtClaimsSet.Builder> extra) {
        JwtClaimsSet.Builder claims = claimsFor(0);
        extra.accept(claims);
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private JwtClaimsSet.Builder claimsFor(long userId) {
        Instant now = ZonedDateTime.of(TODAY, NINE_AM, office).toInstant();
        return JwtClaimsSet.builder()
                .issuer(localAuth.issuer())
                .audience(List.of(localAuth.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(localAuth.tokenTtl()));
    }

    /** Flip the last character of the signature; header and payload stay untouched and valid. */
    /**
     * Flip one bit of the signature, and prove the flip actually landed.
     *
     * <p>The self-check is the point. Editing the <em>final</em> base64url character of an HS256
     * signature usually changes nothing: 32 bytes need 43 characters, so the last one carries just
     * two significant bits and the other four are discarded padding. Four of the sixty-four
     * alphabet characters decode to byte-identical output. A tamper helper that quietly produced a
     * valid token would make this test assert that a good token is accepted while claiming to prove
     * a forged one is rejected — so it verifies its own premise before handing the token over.
     */
    private static String tamperWithSignature(String token) {
        int lastDot = token.lastIndexOf('.');
        String signature = token.substring(lastDot + 1);
        byte[] original = Base64.getUrlDecoder().decode(signature);

        byte[] flipped = original.clone();
        flipped[0] ^= 0x01;

        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(flipped);
        assertThat(Base64.getUrlDecoder().decode(tampered))
                .as("the tamper must change the decoded signature bytes, or this test proves nothing")
                .isNotEqualTo(original);

        return token.substring(0, lastDot + 1) + tampered;
    }

    /**
     * The attack the signature exists to stop: keep the issuer's signature, rewrite the claims.
     * Here the subject is swapped for the admin's id, which is what an attacker who wants somebody
     * else's privileges would actually edit.
     */
    private String tamperWithPayload(String token, long impersonatedUserId) {
        String[] parts = token.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String rewritten = claims.replaceFirst(
                "\"sub\"\\s*:\\s*\"[^\"]*\"", "\"sub\":\"" + impersonatedUserId + "\"");

        assertThat(rewritten)
                .as("the payload rewrite must actually change the claims")
                .isNotEqualTo(claims);

        return parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(rewritten.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];
    }

    private static SecretKey randomKey() {
        byte[] material = new byte[32];
        new SecureRandom().nextBytes(material);
        return new SecretKeySpec(material, "HmacSHA256");
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return body(actions.andReturn());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void moveClockTo(LocalTime timeOfDay) {
        clock.setTo(ZonedDateTime.of(TODAY, timeOfDay, office));
    }

    private void clearPeople() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    private long person(String email, String displayName, UserRole role, String password) {
        AppUser user = new AppUser(email, displayName, role);
        user.setPasswordHash(passwordEncoder.encode(password));
        return users.saveAndFlush(user).getId();
    }

    private void deactivate(long userId) {
        AppUser user = users.findById(userId).orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);
    }

    private void setRole(long userId, UserRole role) {
        AppUser user = users.findById(userId).orElseThrow();
        user.setRole(role);
        users.saveAndFlush(user);
    }
}
