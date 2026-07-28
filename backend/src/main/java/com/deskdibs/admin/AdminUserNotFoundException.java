package com.deskdibs.admin;

/** No user with that id, on an administrative lookup. */
public class AdminUserNotFoundException extends AdminException {

    private final long userId;

    public AdminUserNotFoundException(long userId) {
        super("No user with id " + userId);
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    @Override
    public AdminErrorCode errorCode() {
        return AdminErrorCode.ADMIN_USER_NOT_FOUND;
    }
}
