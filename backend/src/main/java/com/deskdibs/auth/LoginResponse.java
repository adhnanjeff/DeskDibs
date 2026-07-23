package com.deskdibs.auth;

/**
 * What a successful local login returns.
 *
 * <p>The user block is included so the client does not have to follow up with
 * {@code GET /api/auth/me} just to render a name — and, more usefully, so the role it displays
 * comes from the database on this very response rather than from anything it could have read out
 * of the token itself.
 *
 * @param accessToken      the bearer token. Never log this record whole
 * @param tokenType        always {@code Bearer}, spelled out so a client need not assume
 * @param expiresInSeconds lifetime from issue
 * @param user             the caller, as the database describes them
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        CurrentUserResponse user) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(IssuedToken token, CurrentUserResponse user) {
        return new LoginResponse(token.value(), BEARER, token.expiresInSeconds(), user);
    }
}
