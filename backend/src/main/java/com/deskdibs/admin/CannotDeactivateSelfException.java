package com.deskdibs.admin;

/**
 * An administrator tried to deactivate their own account.
 *
 * <p>Refused because deactivation immediately releases the account's desks and refuses it at login:
 * an administrator who did this to themselves would be locked out of the only surface that could
 * undo it, and in a single-administrator office that leaves the system with no way back in short of
 * editing the database by hand. Deactivating somebody else is exactly what this endpoint is for —
 * this guard is only about the one account that cannot fix the mistake afterwards.
 */
public class CannotDeactivateSelfException extends AdminException {

    private final long userId;

    public CannotDeactivateSelfException(long userId) {
        super("User " + userId + " may not deactivate their own account");
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    @Override
    public AdminErrorCode errorCode() {
        return AdminErrorCode.CANNOT_DEACTIVATE_SELF;
    }
}
