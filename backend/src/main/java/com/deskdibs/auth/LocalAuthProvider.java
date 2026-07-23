package com.deskdibs.auth;

import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Identity resolution for locally issued tokens.
 *
 * <p>The subject is an {@code app_user} id, written by {@link LocalTokenIssuer}, and by the time
 * this runs the token's signature, expiry, issuer and audience have already been checked. So the
 * work is small and the interesting part is what it refuses:
 *
 * <ul>
 *   <li>a subject that names no row — a token that outlived the account it was minted for;</li>
 *   <li>a row that is no longer {@code active}.</li>
 * </ul>
 *
 * <p>The second is why the row is re-read on every request instead of the role and status being
 * baked into the token. Deactivation has to bite immediately, not whenever the last issued token
 * happens to expire. It costs one primary-key lookup.
 *
 * <p>No provisioning here, unlike the Entra provider: a local password has to be set by somebody
 * deliberately, and a provider that created accounts on sight of a token it signed itself would
 * be creating them on sight of its own output.
 */
@Component
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "local")
public class LocalAuthProvider implements AuthProvider {

    private final AppUserRepository users;

    public LocalAuthProvider(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public AuthProviderKind kind() {
        return AuthProviderKind.LOCAL;
    }

    @Override
    public AuthenticatedUser resolve(Jwt token) {
        long userId = subjectAsUserId(token);
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new UnknownPrincipalException("no app_user with id " + userId));

        if (!user.isActive()) {
            throw new UserDeactivatedException(user.getId());
        }
        return AuthenticatedUser.of(user);
    }

    private static long subjectAsUserId(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnknownPrincipalException("token carries no subject");
        }
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException notAnId) {
            // Signed by us, so this is our own bug or a key shared with something else entirely.
            throw new UnknownPrincipalException("subject '" + subject + "' is not an app_user id");
        }
    }
}
