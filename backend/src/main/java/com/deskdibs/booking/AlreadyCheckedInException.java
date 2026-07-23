package com.deskdibs.booking;

import java.time.OffsetDateTime;

/**
 * Already checked in.
 *
 * <p>Refused rather than silently overwritten, because {@code checked_in_at} is the evidence the
 * no-show release reads: quietly moving it later would let a stale retry keep resetting the clock.
 */
public class AlreadyCheckedInException extends BookingException {

    private final long bookingId;
    private final OffsetDateTime checkedInAt;

    public AlreadyCheckedInException(long bookingId, OffsetDateTime checkedInAt) {
        super("Booking " + bookingId + " was already checked in at " + checkedInAt);
        this.bookingId = bookingId;
        this.checkedInAt = checkedInAt;
    }

    public long getBookingId() {
        return bookingId;
    }

    /** When the original check-in happened. */
    public OffsetDateTime getCheckedInAt() {
        return checkedInAt;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.ALREADY_CHECKED_IN;
    }
}
