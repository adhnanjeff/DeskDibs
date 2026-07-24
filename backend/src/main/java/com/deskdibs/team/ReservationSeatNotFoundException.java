package com.deskdibs.team;

/**
 * One of the requested seat ids does not exist.
 *
 * <p>Refused outright rather than folded into the partial-success report: an unknown id is a
 * malformed request (a typo, a stale id for a seat that no longer exists), not the real-world
 * "already booked" conflict the report exists to describe.
 */
public class ReservationSeatNotFoundException extends ReservationException {

    private final long seatId;

    public ReservationSeatNotFoundException(long seatId) {
        super("No seat with id " + seatId);
        this.seatId = seatId;
    }

    public long getSeatId() {
        return seatId;
    }

    @Override
    public ReservationErrorCode errorCode() {
        return ReservationErrorCode.SEAT_NOT_FOUND;
    }
}
