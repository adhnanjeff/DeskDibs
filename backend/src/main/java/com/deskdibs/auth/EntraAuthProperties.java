package com.deskdibs.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Microsoft Entra ID app registration, once IT issues one.
 *
 * <p>Blank is legal here, because these bind unconditionally while development still runs on the
 * local provider — see {@link LocalAuthProperties} for the same reasoning. What makes them
 * mandatory is {@link EntraAuthConfiguration}, which refuses to start when the provider is
 * actually {@code entra} and either identifier is missing.
 *
 * <p>{@code issuer} and {@code jwkSetUri} default to the worldwide cloud's well-known endpoints
 * for the tenant, and exist as overrides for a sovereign cloud (US Government, China) where the
 * hostname differs. Neither is a secret; the client secret is not here at all, because a bearer
 * token resource server never presents one.
 *
 * @param tenantId  Entra directory (tenant) id, {@code ENTRA_TENANT_ID}
 * @param clientId  application (client) id, {@code ENTRA_CLIENT_ID}. Required as the {@code aud}
 *                  a presented token must carry
 * @param issuer    override for the expected {@code iss}; derived from the tenant when blank
 * @param jwkSetUri override for the signing-key endpoint; derived from the tenant when blank
 */
@ConfigurationProperties(prefix = "deskdibs.auth.entra")
public record EntraAuthProperties(String tenantId, String clientId, String issuer, String jwkSetUri) {

    private static final String DEFAULT_ISSUER_TEMPLATE = "https://login.microsoftonline.com/%s/v2.0";
    private static final String DEFAULT_JWK_SET_TEMPLATE =
            "https://login.microsoftonline.com/%s/discovery/v2.0/keys";

    public EntraAuthProperties {
        tenantId = trimmed(tenantId);
        clientId = trimmed(clientId);
        issuer = trimmed(issuer);
        jwkSetUri = trimmed(jwkSetUri);
    }

    /** The {@code iss} a presented token must carry. */
    public String resolvedIssuer() {
        return issuer.isEmpty() ? DEFAULT_ISSUER_TEMPLATE.formatted(tenantId) : issuer;
    }

    /** Where the tenant publishes the public keys its tokens are signed with. */
    public String resolvedJwkSetUri() {
        return jwkSetUri.isEmpty() ? DEFAULT_JWK_SET_TEMPLATE.formatted(tenantId) : jwkSetUri;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
