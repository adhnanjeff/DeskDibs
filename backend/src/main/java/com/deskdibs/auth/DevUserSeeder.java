package com.deskdibs.auth;

import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The three demo accounts a developer needs to exercise the role rules.
 *
 * <h2>Why this is a component and not a migration</h2>
 * Flyway migrations run everywhere, in order, including on the production database — that is the
 * entire point of them. A {@code V4__seed_dev_users.sql} would therefore put three accounts with a
 * known password into the company's schema, and the only thing standing between that and a real
 * sign-in would be remembering to delete them. Seeding from a bean means the accounts exist
 * exactly where the local provider does, which is nowhere near production:
 * {@link LocalProductionProfileGuard} refuses to start under a production profile, and under
 * {@code AUTH_PROVIDER=entra} this class is not even instantiated.
 *
 * <h2>Why a blank password disables it</h2>
 * The obvious alternative — generate a password and log it, the way Spring Boot does for its
 * default user — is ruled out by this project's standard that no secret appears in source, logs,
 * or git. So {@code DEV_SEED_PASSWORD} has no default anywhere, exactly like {@code DB_PASSWORD},
 * and when it is unset nothing is seeded and a line explains why. Fail closed: an account nobody
 * asked for is worse than an account that is missing.
 *
 * <h2>Idempotent</h2>
 * An account that already exists is left completely alone — not re-hashed, not re-roled, not
 * re-activated. A restart must never quietly hand back access to an account somebody deactivated
 * on purpose, and must never overwrite a password a developer changed.
 */
@Component
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "local")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    /**
     * One account per role, so every branch of every authorization rule has somebody to exercise
     * it. The {@code .local} suffix is reserved by RFC 6762 and can never be a real mailbox, so
     * these addresses cannot collide with a genuine colleague's.
     */
    private static final List<DevUser> DEV_USERS = List.of(
            new DevUser("employee@deskdibs.local", "Dev Employee", UserRole.EMPLOYEE),
            new DevUser("manager@deskdibs.local", "Dev Manager", UserRole.MANAGER),
            new DevUser("admin@deskdibs.local", "Dev Admin", UserRole.ADMIN));

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LocalAuthProperties properties;

    public DevUserSeeder(AppUserRepository users,
                         PasswordEncoder passwordEncoder,
                         LocalAuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /**
     * Create any missing demo account.
     *
     * <p>Public and callable directly so a test can drive it at a moment of its choosing rather
     * than depending on what startup happened to do — the suite shares one database across test
     * classes, several of which clear {@code app_user} between methods.
     *
     * @return the addresses created by this call, empty when everything already existed or when
     *         seeding is switched off
     */
    @Transactional
    public List<String> seed() {
        if (!properties.seedDevUsers()) {
            return List.of();
        }
        if (properties.seedPassword().isBlank()) {
            log.info("""
                    Local auth: dev user seeding is skipped because DEV_SEED_PASSWORD is not set. \
                    Set it to any value and restart to create {}. No password is invented or \
                    logged for you.""", addresses());
            return List.of();
        }

        // Hashed once for all three: BCrypt is deliberately slow, and this is the same secret.
        String hash = passwordEncoder.encode(properties.seedPassword());

        List<String> created = DEV_USERS.stream()
                .filter(devUser -> users.findByEmailIgnoreCase(devUser.email()).isEmpty())
                .map(devUser -> create(devUser, hash))
                .toList();

        if (created.isEmpty()) {
            log.info("Local auth: all dev users already present, nothing seeded.");
        } else {
            log.info("Local auth: seeded dev users {} with the configured DEV_SEED_PASSWORD.", created);
        }
        return created;
    }

    private String create(DevUser devUser, String passwordHash) {
        AppUser user = new AppUser(devUser.email(), devUser.displayName(), devUser.role());
        user.setPasswordHash(passwordHash);
        users.saveAndFlush(user);
        return devUser.email();
    }

    private static List<String> addresses() {
        return DEV_USERS.stream().map(DevUser::email).toList();
    }

    private record DevUser(String email, String displayName, UserRole role) {
    }
}
