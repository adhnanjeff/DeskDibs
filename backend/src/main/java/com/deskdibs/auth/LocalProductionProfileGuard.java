package com.deskdibs.auth;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to let the development identity provider start in production.
 *
 * <p>The local provider checks a password against a row in this database and mints its own tokens.
 * That is exactly right for a laptop and catastrophic on a company deployment: anybody who can
 * write to {@code app_user.password_hash} would hold a valid identity, and the Entra tenant — the
 * actual source of truth about who works here — would be bypassed entirely.
 *
 * <p>A misconfigured {@code AUTH_PROVIDER} is not a hypothetical. It is one stale environment
 * variable in a deployment pipeline, and it fails <em>silently</em>: the application starts, logins
 * work, and nothing looks wrong until somebody notices that leavers can still sign in. So it is
 * turned into the loudest possible failure instead — the context does not refresh and the process
 * does not serve a single request.
 *
 * <p>A static method rather than a lifecycle callback, so the rule can be tested for what it is:
 * a decision about an {@link Environment}, with no container needed to reach it.
 */
public final class LocalProductionProfileGuard {

    /** Profile names that mean "this is not a laptop". */
    private static final List<String> PRODUCTION_PROFILES = List.of("prod", "production");

    private LocalProductionProfileGuard() {
    }

    /**
     * @throws IllegalStateException a production profile is active, which stops the context
     */
    public static void assertNotProduction(Environment environment) {
        Arrays.stream(environment.getActiveProfiles())
                .filter(profile -> PRODUCTION_PROFILES.contains(profile.toLowerCase(Locale.ROOT)))
                .findFirst()
                .ifPresent(LocalProductionProfileGuard::refuse);
    }

    private static void refuse(String profile) {
        throw new IllegalStateException("""
                AUTH_PROVIDER=local is the development identity provider and must never run with \
                the '%s' profile active. It authenticates against password hashes in this \
                database and signs its own tokens, which bypasses the Entra tenant entirely. \
                Set AUTH_PROVIDER=entra, or drop the production profile if this really is a \
                development environment.""".formatted(profile));
    }
}
