package com.deskdibs.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The single point where a validated token becomes an authenticated caller.
 *
 * <p>This replaces Spring Security's default {@code JwtAuthenticationConverter}, and replacing it
 * is the whole point. The default reads a claim — {@code scope}, {@code scp}, or whatever
 * {@code setAuthoritiesClaimName} is pointed at — and turns it into granted authorities. That
 * makes the token the source of permissions. A token is issued once and lives for hours; a role is
 * a fact about the person right now, revocable by an administrator. Only one of those can be
 * authoritative, and it is not the token.
 *
 * <p>So: the token is asked <em>only</em> who the caller is. The {@link AuthProvider} answers with
 * an {@link AuthenticatedUser} loaded from {@code app_user}, and the authorities on the resulting
 * {@link AuthenticatedUserToken} are derived from {@code app_user.role}. A token asserting
 * {@code "roles": ["ADMIN"]} against a row that says {@code EMPLOYEE} produces
 * {@code ROLE_EMPLOYEE} and nothing else — there is no code path by which the claim could win,
 * because no code path reads it.
 *
 * <p>Refusals thrown from here are {@link AuthException}s, which are
 * {@code AuthenticationException}s, so an unresolvable or deactivated principal leaves through the
 * {@code AuthenticationEntryPoint} as 401 JSON rather than as a 500.
 */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthProvider authProvider;

    public JwtPrincipalConverter(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        return new AuthenticatedUserToken(authProvider.resolve(token), token);
    }
}
