package com.deskdibs.auth;

import com.deskdibs.user.AppUser;
import com.deskdibs.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated caller, as the application knows them — never as a token described them.
 *
 * <p>Every field here is read from {@code app_user}. A token may carry {@code roles},
 * {@code groups}, {@code wids} or {@code scp}; none of them are read anywhere in this codebase.
 * A token is only ever a claim about <em>who</em> the caller is. <em>What they may do</em> is
 * answered by the database row that identity resolves to, which is the only copy an administrator
 * can revoke.
 *
 * <p>Only an {@code active} user is ever turned into one of these: both providers refuse a
 * deactivated account before this record is constructed. Downstream code may therefore treat the
 * existence of an {@code AuthenticatedUser} as proof the account is live, and does not re-check.
 *
 * @param id          {@code app_user.id} — this is the value Phase 4 controllers pass to
 *                    {@code BookingService} as {@code actingUserId}
 * @param email       {@code app_user.email}
 * @param displayName {@code app_user.display_name}
 * @param role        {@code app_user.role}, the sole input to every role-based rule
 */
public record AuthenticatedUser(long id, String email, String displayName, UserRole role) {

    /** Spring Security's convention: {@code hasRole('ADMIN')} looks for {@code ROLE_ADMIN}. */
    private static final String ROLE_PREFIX = "ROLE_";

    public static AuthenticatedUser of(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    /** Derived from the stored role, so a token cannot add one. */
    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
    }
}
