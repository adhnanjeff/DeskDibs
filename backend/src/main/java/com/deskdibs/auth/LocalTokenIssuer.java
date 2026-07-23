package com.deskdibs.auth;

import com.deskdibs.common.OfficeClock;
import com.deskdibs.user.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints the access token a successful local login hands back.
 *
 * <h2>What is deliberately not in the token</h2>
 * The user's role. It would be convenient — the frontend could hide the admin menu without another
 * request — and it would be a standing invitation for some future line of code to trust it. A
 * token is issued once and lives for hours; a role is a fact about the person right now, revocable
 * by an administrator. Keeping the role out means there is nothing to be tempted by: the role is
 * read from {@code app_user} on every request, and {@code GET /api/auth/me} is how a client learns
 * it. The Entra provider is in the same position by nature, so both providers behave identically.
 *
 * <p>What the token does carry is a subject — the {@code app_user} id — plus email and display
 * name for log correlation, and a {@code jti} so an individual token can be identified in a
 * support conversation without anybody having to paste the token itself.
 *
 * <p>Times come from {@link OfficeClock}, the same source the rest of the system uses for "now",
 * so an issued lifetime means the same thing here as everywhere else and a test can move it.
 */
@Component
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "local")
public class LocalTokenIssuer {

    private final JwtEncoder encoder;
    private final LocalAuthProperties properties;
    private final OfficeClock officeClock;

    public LocalTokenIssuer(JwtEncoder encoder, LocalAuthProperties properties, OfficeClock officeClock) {
        this.encoder = encoder;
        this.properties = properties;
        this.officeClock = officeClock;
    }

    public IssuedToken issue(AppUser user) {
        Instant issuedAt = officeClock.instant();
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .build();

        String value = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedToken(value, properties.tokenTtl().toSeconds());
    }
}
