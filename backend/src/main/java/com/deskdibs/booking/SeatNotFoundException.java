package com.deskdibs.booking;

/** The claim referenced a seat that does not exist. */
public class SeatNotFoundException extends BookingException {

    private final long seatId;

    public SeatNotFoundException(long seatId) {
        super("No seat with id " + seatId);
        this.seatId = seatId;
    }

    public long getSeatId() {
        return seatId;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.SEAT_NOT_FOUND;
    }
}
