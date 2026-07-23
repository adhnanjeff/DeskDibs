package com.deskdibs.auth;

import com.deskdibs.user.UserRole;

/**
 * Who the server says you are.
 *
 * <p>The {@code role} here is the one authorization actually uses — read from {@code app_user},
 * not echoed back from a token. That makes this endpoint the honest answer to "am I an admin",
 * and the reason {@link LocalTokenIssuer} can leave the role out of the token entirely.
 *
 * <p>{@code provider} is reported so a client can tell a development session from an SSO one
 * (for instance, to decide whether to offer a "sign out of Microsoft" link). It is descriptive
 * only; nothing is authorized on it.
 */
public record CurrentUserResponse(
        long id,
        String email,
        String displayName,
        UserRole role,
        AuthProviderKind provider) {

    public static CurrentUserResponse of(AuthenticatedUser user, AuthProviderKind provider) {
        return new CurrentUserResponse(user.id(), user.email(), user.displayName(), user.role(), provider);
    }
}
