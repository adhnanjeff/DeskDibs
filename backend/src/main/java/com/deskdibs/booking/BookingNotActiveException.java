package com.deskdibs.booking;

/**
 * The booking is no longer ACTIVE, so there is nothing left to cancel or check into.
 *
 * <p>Refused rather than treated as a silent no-op: a client cancelling a booking that was
 * auto-released as a no-show has a different story to tell than one cancelling a live seat.
 */
public class BookingNotActiveException extends BookingException {

    private final long bookingId;
    private final BookingStatus status;

    public BookingNotActiveException(long bookingId, BookingStatus status) {
        super("Booking " + bookingId + " is " + status + ", not ACTIVE");
        this.bookingId = bookingId;
        this.status = status;
    }

    public long getBookingId() {
        return bookingId;
    }

    /** What the booking actually is: CANCELLED, or RELEASED_NO_SHOW. */
    public BookingStatus getStatus() {
        return status;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.BOOKING_NOT_ACTIVE;
    }
}
