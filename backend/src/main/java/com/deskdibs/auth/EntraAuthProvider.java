package com.deskdibs.auth;

import com.deskdibs.user.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Identity resolution for Microsoft Entra ID tokens.
 *
 * <p>By the time this runs, {@link EntraAuthConfiguration}'s decoder has already checked the
 * token's signature against the tenant's published keys, its expiry, its issuer and its audience.
 * What is left is the question the token cannot answer on its own: which row in {@code app_user}
 * is this, and may they sign in?
 *
 * <p>Three steps, and the ordering is the point.
 * <ol>
 *   <li>Take only {@code oid}, an email claim and {@code name} from the token
 *       ({@link EntraIdentity}). Every other claim, including anything about roles or groups, is
 *       dropped here.</li>
 *   <li>Find or create the row ({@link EntraUserProvisioner}), always at
 *       {@code EMPLOYEE} when creating.</li>
 *   <li>Refuse the row if it is deactivated — checked last, so it applies equally to an account
 *       provisioned seconds ago and one that has existed for years.</li>
 * </ol>
 *
 * <p>The result is an {@link AuthenticatedUser} identical in kind to the local provider's, which
 * is what lets every authorization rule downstream be written once.
 */
@Component
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "entra")
public class EntraAuthProvider implements AuthProvider {

    private final EntraUserProvisioner provisioner;

    public EntraAuthProvider(EntraUserProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    @Override
    public AuthProviderKind kind() {
        return AuthProviderKind.ENTRA;
    }

    @Override
    public AuthenticatedUser resolve(Jwt token) {
        AppUser user = provisioner.resolve(EntraIdentity.from(token));

        if (!user.isActive()) {
            throw new UserDeactivatedException(user.getId());
        }
        return AuthenticatedUser.of(user);
    }
}
