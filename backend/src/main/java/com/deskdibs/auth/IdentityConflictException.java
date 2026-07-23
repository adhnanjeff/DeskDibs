package com.deskdibs.auth;

import java.io.Serial;

/**
 * The token's external id and its email point at two different {@code app_user} rows.
 *
 * <p>It happens when an address is recycled — someone leaves, a new joiner is given the same
 * mailbox, and the tenant issues them a different object id. Silently re-pointing the old row at
 * the new person would hand the new joiner the leaver's bookings and history; silently creating a
 * second row would collide with {@code uq_app_user_email}. Both are guesses about a person's
 * identity, so this refuses instead and leaves it to an administrator.
 *
 * <p>Fail closed. The log line carries enough to fix the data; the response says nothing about it.
 */
public class IdentityConflictException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUBLIC_MESSAGE =
            "This identity could not be matched to an account. Contact an administrator.";

    public IdentityConflictException(String externalId, String email, long conflictingUserId) {
        super(AuthErrorCode.IDENTITY_CONFLICT,
                "External id " + externalId + " presented email " + email
                        + ", which already belongs to user " + conflictingUserId
                        + " under a different external id",
                PUBLIC_MESSAGE);
    }
}
