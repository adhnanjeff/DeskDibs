package com.deskdibs.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * An endpoint that only an administrator may reach, so "does not grant admin power" can be tested
 * as an actual refusal rather than as an assertion about a list of authorities.
 *
 * <p>It lives in the test sources deliberately. Phase 3 ships no admin features, and adding a real
 * endpoint to production code purely to have something to point a test at would be worse than the
 * gap it closes. What it exercises is entirely production machinery: {@code @EnableMethodSecurity}
 * from {@link SecurityConfig}, the authorities on {@link AuthenticatedUserToken}, and
 * {@link JsonAccessDeniedHandler} writing the 403.
 *
 * <p>{@code hasRole('ADMIN')} resolves against {@code ROLE_ADMIN}, which
 * {@link AuthenticatedUser#authorities()} derives from {@code app_user.role} — never from a claim.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AdminOnlyTestEndpoint {

    public static final String PATH = "/test/admin-only";

    /**
     * Registered by virtue of being a member class of an imported configuration — deliberately not
     * also declared as a {@code @Bean} here, which would map the same path twice. Nested classes of
     * a {@code @TestConfiguration} are excluded from the application's component scan, so this
     * endpoint exists only in the contexts that import it.
     */
    @RestController
    public static class AdminOnlyController {

        @GetMapping(PATH)
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, String> adminOnly() {
            return Map.of("granted", "true");
        }
    }
}
