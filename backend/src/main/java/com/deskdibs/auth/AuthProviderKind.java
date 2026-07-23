package com.deskdibs.auth;

/**
 * Which identity provider is wired in.
 *
 * <p>The Entra app registration does not exist yet, so development runs on {@link #LOCAL} and
 * flips to {@link #ENTRA} by configuration alone. Nothing that makes an authorization decision
 * ever branches on this value: both providers hand back the same {@link AuthenticatedUser},
 * resolved from {@code app_user}, and every rule downstream is written against that.
 *
 * <p>Bound from {@code deskdibs.auth.provider} (env {@code AUTH_PROVIDER}). A value that is
 * neither of these fails to bind, which stops the application at startup rather than leaving it
 * running with no provider at all.
 */
public enum AuthProviderKind {

    /** Email + password against {@code app_user}, tokens signed by this application. Dev only. */
    LOCAL,

    /** Microsoft Entra ID tokens, validated against the tenant's JWKS endpoint. */
    ENTRA;

    /** The property both provider implementations switch on. A constant so they cannot drift. */
    public static final String PROPERTY = "deskdibs.auth.provider";
}
