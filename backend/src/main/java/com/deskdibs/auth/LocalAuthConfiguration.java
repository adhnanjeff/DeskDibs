package com.deskdibs.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Everything the local development provider needs, and nothing when it is switched off.
 *
 * <p>Active only while {@code AUTH_PROVIDER=local}. Under {@code entra} not one bean in this file
 * exists — no password encoder, no token issuer, no login endpoint — so the development sign-in
 * path is absent from a production deployment rather than merely unused by it.
 *
 * <p>The constructor runs two checks before any bean is built, because both are cheaper to hit at
 * startup than in production: this provider must not be running under a production profile, and
 * its signing key must be strong enough for HS256.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "local")
public class LocalAuthConfiguration {

    /**
     * Issuer and verifier share one clock, so there is no genuine skew between them. The small
     * allowance is for the deployment shape this does not have yet — two instances behind a load
     * balancer, drifting a little apart — and is far below the token lifetime.
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    public LocalAuthConfiguration(Environment environment) {
        LocalProductionProfileGuard.assertNotProduction(environment);
    }

    @Bean
    public LocalJwtSigningKey localJwtSigningKey(LocalAuthProperties properties) {
        return LocalJwtSigningKey.from(properties);
    }

    /**
     * Verifies locally issued tokens.
     *
     * <p>Signature, expiry, issuer and audience — all four, none optional. Checking only the
     * signature would let a token minted for another service against the same secret be replayed
     * here, and would leave an expired one indistinguishable from a live one.
     *
     * <p>The timestamp validator is given the application's {@link Clock} bean rather than the
     * system clock. That is what makes expiry testable: a test moves the office clock past a
     * token's lifetime and the token stops working, with no sleeping and no waiting for eight
     * hours to pass.
     */
    @Bean
    public JwtDecoder jwtDecoder(LocalJwtSigningKey signingKey, LocalAuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(signingKey.key())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                audienceValidator(properties.audience()))));

        return decoder;
    }

    /** Mints locally issued tokens with the same key the decoder verifies. */
    @Bean
    public JwtEncoder jwtEncoder(LocalJwtSigningKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(signingKey.key()));
    }

    /**
     * BCrypt at the library default cost.
     *
     * <p>Only ever used by the local provider: Entra users have no {@code password_hash} at all,
     * so under SSO no password is stored, compared, or transmitted anywhere in this system.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                claimed -> claimed != null && claimed.contains(audience));
    }
}
