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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The development account seeder.
 *
 * <p>Driven by calling {@link DevUserSeeder#seed()} rather than by letting startup do it. The
 * suite shares one database across test classes and several of them clear {@code app_user} between
 * methods, so rows created once at context refresh would be present or absent depending on which
 * class ran first — a test that passes or fails on ordering proves nothing. Calling the component
 * is the same code path startup takes, at a moment this test controls.
 *
 * <p>The seed password is generated per run, so no credential is written down in this repository
 * even for a throwaway container.
 */
class DevUserSeederTest extends AbstractPostgresIntegrationTest {

    private static final String SEED_PASSWORD = UUID.randomUUID().toString();

    private static final List<String> DEV_ADDRESSES = List.of(
            "employee@deskdibs.local", "manager@deskdibs.local", "admin@deskdibs.local");

    @DynamicPropertySource
    static void enableSeeding(DynamicPropertyRegistry registry) {
        registry.add("deskdibs.auth.local.seed-dev-users", () -> true);
        registry.add("deskdibs.auth.local.seed-password", () -> SEED_PASSWORD);
    }

    private final DevUserSeeder seeder;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;
    private final LocalAuthProperties properties;

    DevUserSeederTest(DevUserSeeder seeder,
                      AppUserRepository users,
                      BookingRepository bookings,
                      PasswordEncoder passwordEncoder,
                      LocalAuthProperties properties) {
        this.seeder = seeder;
        this.users = users;
        this.bookings = bookings;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @BeforeEach
    void emptyTheDirectory() {
        clearPeople();
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearPeople();
    }

    @Test
    @DisplayName("seeding creates one account per role, each able to log in with the configured password")
    void seedingCreatesOneAccountPerRole() {
        assertThat(seeder.seed()).containsExactlyElementsOf(DEV_ADDRESSES);

        assertThat(users.findAll())
                .extracting(AppUser::getEmail, AppUser::getRole, AppUser::isActive)
                .containsExactlyInAnyOrder(
                        tuple("employee@deskdibs.local", UserRole.EMPLOYEE, true),
                        tuple("manager@deskdibs.local", UserRole.MANAGER, true),
                        tuple("admin@deskdibs.local", UserRole.ADMIN, true));

        AppUser admin = users.findByEmailIgnoreCase("admin@deskdibs.local").orElseThrow();
        assertThat(admin.getPasswordHash())
                .as("the password is stored hashed, never as text")
                .isNotBlank()
                .isNotEqualTo(SEED_PASSWORD)
                .startsWith("$2");
        assertThat(passwordEncoder.matches(SEED_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(admin.getExternalId())
                .as("a local account has no Entra object id")
                .isNull();
    }

    @Test
    @DisplayName("seeding twice creates nothing the second time and overwrites nothing the first")
    void seedingTwiceIsIdempotentAndNonDestructive() {
        seeder.seed();

        // Somebody deactivated the demo admin and changed its password on purpose. A restart must
        // not quietly hand that access back.
        AppUser admin = users.findByEmailIgnoreCase("admin@deskdibs.local").orElseThrow();
        admin.setActive(false);
        admin.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        String hashAfterChange = users.saveAndFlush(admin).getPasswordHash();

        assertThat(seeder.seed()).isEmpty();

        AppUser afterSecondSeed = users.findByEmailIgnoreCase("admin@deskdibs.local").orElseThrow();
        assertThat(users.count()).isEqualTo(DEV_ADDRESSES.size());
        assertThat(afterSecondSeed.isActive()).isFalse();
        assertThat(afterSecondSeed.getPasswordHash()).isEqualTo(hashAfterChange);
    }

    @Test
    @DisplayName("no DEV_SEED_PASSWORD means no accounts, rather than an invented password")
    void aBlankSeedPasswordSeedsNothing() {
        LocalAuthProperties withoutPassword = new LocalAuthProperties(
                properties.jwtSecret(), properties.tokenTtl(), properties.issuer(),
                properties.audience(), true, "");

        DevUserSeeder disabled = new DevUserSeeder(users, passwordEncoder, withoutPassword);

        assertThat(disabled.seed()).isEmpty();
        assertThat(users.count())
                .as("nothing is created when there is no configured password to create it with")
                .isZero();
    }

    private void clearPeople() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
    }
}
