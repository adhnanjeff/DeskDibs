package com.deskdibs.admin;

/** No seat with that id, on an administrative lookup. */
public class AdminSeatNotFoundException extends AdminException {

    private final long seatId;

    public AdminSeatNotFoundException(long seatId) {
        super("No seat with id " + seatId);
        this.seatId = seatId;
    }

    public long getSeatId() {
        return seatId;
    }

    @Override
    public AdminErrorCode errorCode() {
        return AdminErrorCode.ADMIN_SEAT_NOT_FOUND;
    }
}
