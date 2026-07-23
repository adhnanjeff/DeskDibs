package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * Check-in only means anything for today's seat.
 *
 * <p>Both dates come from the office clock's notion of today, never from the client's.
 */
public class CheckInNotForTodayException extends BookingException {

    private final long bookingId;
    private final LocalDate bookingDate;
    private final LocalDate today;

    public CheckInNotForTodayException(long bookingId, LocalDate bookingDate, LocalDate today) {
        super("Booking " + bookingId + " is for " + bookingDate + ", not today (" + today + ")");
        this.bookingId = bookingId;
        this.bookingDate = bookingDate;
        this.today = today;
    }

    public long getBookingId() {
        return bookingId;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    /** Today in the office timezone. */
    public LocalDate getToday() {
        return today;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.CHECK_IN_NOT_FOR_TODAY;
    }
}
