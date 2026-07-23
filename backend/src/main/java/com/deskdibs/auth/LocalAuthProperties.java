package com.deskdibs.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the local development identity provider.
 *
 * <p>Every one of these is bound unconditionally — {@code @ConfigurationPropertiesScan} does not
 * evaluate conditions — so nothing here is {@code @NotBlank}. A blank value must be legal when
 * this provider is switched off. The values that genuinely matter are checked by
 * {@link LocalAuthConfiguration} at startup, which only runs when the provider is active.
 *
 * @param jwtSecret    HMAC signing material, from {@code DESKDIBS_JWT_SECRET}. Blank means "mint a
 *                     random one at startup"; there is deliberately no default in any committed
 *                     file, because a default signing key is a published signing key
 * @param tokenTtl     how long an issued token stays valid
 * @param issuer       {@code iss} written into issued tokens and required of presented ones
 * @param audience     {@code aud} written into issued tokens and required of presented ones
 * @param seedDevUsers whether to create the demo accounts at startup
 * @param seedPassword the password those accounts get, from {@code DEV_SEED_PASSWORD}. Blank
 *                     disables seeding: no credential is invented, and none is ever logged
 */
@ConfigurationProperties(prefix = "deskdibs.auth.local")
public record LocalAuthProperties(
        String jwtSecret,
        Duration tokenTtl,
        String issuer,
        String audience,
        boolean seedDevUsers,
        String seedPassword) {

    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(8);
    private static final String DEFAULT_ISSUER = "deskdibs-local";
    private static final String DEFAULT_AUDIENCE = "deskdibs-api";

    public LocalAuthProperties {
        tokenTtl = tokenTtl == null ? DEFAULT_TOKEN_TTL : tokenTtl;
        issuer = blankToDefault(issuer, DEFAULT_ISSUER);
        audience = blankToDefault(audience, DEFAULT_AUDIENCE);
        jwtSecret = jwtSecret == null ? "" : jwtSecret.trim();
        seedPassword = seedPassword == null ? "" : seedPassword;
    }

    /** True when a signing key was supplied rather than left to be generated. */
    public boolean hasConfiguredSecret() {
        return !jwtSecret.isEmpty();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
