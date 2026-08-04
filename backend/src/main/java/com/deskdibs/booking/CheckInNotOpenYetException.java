package com.deskdibs.booking;

import java.time.LocalTime;

/**
 * Check-in was attempted before the office's check-in window opened.
 *
 * <p>The window exists because check-in is a claim that you are in the building. Open from midnight
 * it is worth nothing as evidence: somebody could confirm their desk at 00:05 from home, survive the
 * no-show release, and hold a seat all day without ever arriving.
 *
 * <p>Carries both hours so the client can say when to come back rather than only that it refused.
 */
public class CheckInNotOpenYetException extends BookingException {

    private final long bookingId;
    private final LocalTime opensAt;
    private final LocalTime officeTimeNow;

    public CheckInNotOpenYetException(long bookingId, LocalTime opensAt, LocalTime officeTimeNow) {
        super("Check-in for booking " + bookingId + " opens at " + opensAt + " (office time is now "
                + officeTimeNow + ")");
        this.bookingId = bookingId;
        this.opensAt = opensAt;
        this.officeTimeNow = officeTimeNow;
    }

    public long getBookingId() {
        return bookingId;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    /** Wall-clock time in the office, never the caller's. */
    public LocalTime getOfficeTimeNow() {
        return officeTimeNow;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.CHECK_IN_NOT_OPEN_YET;
    }
}
