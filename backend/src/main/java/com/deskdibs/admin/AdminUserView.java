package com.deskdibs.admin;

import com.deskdibs.user.AppUser;
import com.deskdibs.user.UserRole;

/**
 * One person, as the administration screen lists them.
 *
 * <p>Deliberately does not carry {@code passwordHash} or {@code externalId}. The screen needs to
 * know who somebody is and whether their account is live; neither of those two fields helps with
 * that, and a credential hash has no business leaving the persistence layer at all.
 */
public record AdminUserView(
        Long id,
        String email,
        String displayName,
        UserRole role,
        boolean active) {

    static AdminUserView of(AppUser user) {
        return new AdminUserView(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isActive());
    }
}
