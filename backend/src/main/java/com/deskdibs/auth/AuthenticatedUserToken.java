package com.deskdibs.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serial;

/**
 * What sits in the {@code SecurityContext} once a request has been authenticated.
 *
 * <p>Deliberately not {@code JwtAuthenticationToken}. That class makes the token the principal and
 * derives its authorities from the token's own claims, which is exactly the thing this system must
 * never do. Here the principal is an {@link AuthenticatedUser} read from the database and the
 * authorities come from {@code app_user.role}; the {@link Jwt} is demoted to the credentials slot,
 * where it is evidence of <em>who called</em> and nothing more.
 *
 * <p>Anyone tempted to read a role out of {@link #getCredentials()} should read
 * {@link AuthenticatedUser}'s javadoc first.
 */
public final class AuthenticatedUserToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser user;
    private final Jwt token;

    public AuthenticatedUserToken(AuthenticatedUser user, Jwt token) {
        super(user.authorities());
        this.user = user;
        this.token = token;
        setAuthenticated(true);
    }

    /** The validated token. Evidence of identity only — never an input to an authorization rule. */
    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    /** The database id, so log correlation and {@code Authentication#getName} agree on one key. */
    @Override
    public String getName() {
        return String.valueOf(user.id());
    }
}
