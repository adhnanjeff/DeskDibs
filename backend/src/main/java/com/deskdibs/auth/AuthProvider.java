package com.deskdibs.auth;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a token that has already been validated into the person it belongs to.
 *
 * <p>Exactly one implementation is a bean at a time, chosen by {@link AuthProviderKind#PROPERTY}.
 * The seam exists because the Entra app registration does not exist yet: development runs on the
 * local provider today and switches to Entra by configuration, with no authorization logic
 * rewritten. Nothing downstream — not {@code SecurityConfig}, not a controller, not
 * {@code BookingService} — knows or asks which implementation is active. They all see an
 * {@link AuthenticatedUser}.
 *
 * <p><strong>Validation is not this interface's job.</strong> Signature, expiry, issuer and
 * audience are checked by the {@code JwtDecoder} each implementation contributes, before
 * {@link #resolve} is ever called. What is left here is identity resolution: which
 * {@code app_user} row does this subject mean, may that row sign in at all, and — for Entra —
 * does the row need creating on first sight.
 */
public interface AuthProvider {

    /** Which provider this is. For reporting and startup assertions, never for authorization. */
    AuthProviderKind kind();

    /**
     * Resolve a validated token to the caller.
     *
     * @throws UnknownPrincipalException the token is well-formed but names nobody this system knows
     * @throws UserDeactivatedException  the account exists and has been deactivated
     * @throws AuthenticationException   any other refusal; the caller sees 401, never 500
     */
    AuthenticatedUser resolve(Jwt token);
}
