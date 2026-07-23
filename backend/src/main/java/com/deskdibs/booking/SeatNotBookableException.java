package com.deskdibs.booking;

import com.deskdibs.seat.SeatStatus;

/** The seat is out of the pool — DISABLED by an admin, or BROKEN until it is repaired. */
public class SeatNotBookableException extends BookingException {

    private final long seatId;
    private final String seatLabel;
    private final SeatStatus seatStatus;

    public SeatNotBookableException(long seatId, String seatLabel, SeatStatus seatStatus) {
        super("Seat " + seatLabel + " is not bookable (" + seatStatus + ")");
        this.seatId = seatId;
        this.seatLabel = seatLabel;
        this.seatStatus = seatStatus;
    }

    public long getSeatId() {
        return seatId;
    }

    /** Human-facing seat label, e.g. {@code R3-A2}. */
    public String getSeatLabel() {
        return seatLabel;
    }

    public SeatStatus getSeatStatus() {
        return seatStatus;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.SEAT_NOT_BOOKABLE;
    }
}
