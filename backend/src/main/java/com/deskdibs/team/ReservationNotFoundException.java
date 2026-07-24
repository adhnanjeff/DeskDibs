package com.deskdibs.team;

/** No reservation with that id. */
public class ReservationNotFoundException extends ReservationException {

    private final long reservationId;

    public ReservationNotFoundException(long reservationId) {
        super("No reservation with id " + reservationId);
        this.reservationId = reservationId;
    }

    public long getReservationId() {
        return reservationId;
    }

    @Override
    public ReservationErrorCode errorCode() {
        return ReservationErrorCode.RESERVATION_NOT_FOUND;
    }
}
