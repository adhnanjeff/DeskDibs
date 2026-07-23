package com.deskdibs.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Validates Microsoft Entra ID tokens against the tenant's published signing keys.
 *
 * <p>Active only while {@code AUTH_PROVIDER=entra}. This application is a pure resource server
 * under Entra: the browser runs the OIDC code flow (MSAL) and sends the resulting access token,
 * and everything here happens on the way in. There is no client secret, no redirect URI, and no
 * server-side token exchange to get wrong.
 *
 * <h2>What a token has to survive</h2>
 * <ul>
 *   <li><strong>Signature</strong>, against a key fetched from the tenant's JWKS endpoint and
 *       re-fetched when the tenant rotates. Only RS256 is accepted — leaving the algorithm open
 *       is how the {@code alg: none} and HMAC-confusion families of attack work.</li>
 *   <li><strong>Expiry</strong>, on the application's clock, with the small skew a distributed
 *       issuer genuinely needs.</li>
 *   <li><strong>Issuer</strong>, pinned to this tenant. Without it any token signed by any Entra
 *       tenant in the world would satisfy the signature check.</li>
 *   <li><strong>Audience</strong>, pinned to this app registration. Without it a token the user
 *       granted to some other application in the same tenant could be replayed here.</li>
 * </ul>
 *
 * <p>Note what is <em>not</em> checked: {@code roles}, {@code groups}, {@code wids}, {@code scp}.
 * The tenant may put any of them in the token and this system does not read them. Roles come from
 * {@code app_user.role} — see {@link JwtPrincipalConverter}.
 *
 * <h2>Startup with no network</h2>
 * {@code NimbusJwtDecoder.withJwkSetUri} resolves keys lazily, on first use. The context therefore
 * starts without reaching Microsoft, which is what lets this path be wired and compiled against a
 * tenant that does not exist yet — and it means a JWKS outage cannot stop an instance from booting.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "entra")
public class EntraAuthConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EntraAuthConfiguration.class);

    /** Entra is a distributed issuer; a minute is the allowance Microsoft's own guidance assumes. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    public EntraAuthConfiguration(EntraAuthProperties properties) {
        requireConfigured("ENTRA_TENANT_ID", properties.tenantId());
        requireConfigured("ENTRA_CLIENT_ID", properties.clientId());
        log.info("Entra auth: validating tokens issued by {} for audience {}",
                properties.resolvedIssuer(), properties.clientId());
    }

    @Bean
    public JwtDecoder jwtDecoder(EntraAuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.resolvedJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps,
                new JwtIssuerValidator(properties.resolvedIssuer()),
                audienceValidator(properties.clientId()))));

        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                claimed -> claimed != null && claimed.contains(clientId));
    }

    /**
     * A missing identifier stops the context. The alternative is an instance that starts happily
     * and then rejects every token in production, which is the same outage discovered much later.
     */
    private static void requireConfigured(String variable, String value) {
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "AUTH_PROVIDER=entra requires " + variable + ", which is not set. "
                            + "It comes from the Entra app registration; until IT issues one, "
                            + "run with AUTH_PROVIDER=local.");
        }
    }
}
