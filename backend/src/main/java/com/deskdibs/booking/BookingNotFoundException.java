package com.deskdibs.booking;

/** No booking with that id. */
public class BookingNotFoundException extends BookingException {

    private final long bookingId;

    public BookingNotFoundException(long bookingId) {
        super("No booking with id " + bookingId);
        this.bookingId = bookingId;
    }

    public long getBookingId() {
        return bookingId;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.BOOKING_NOT_FOUND;
    }
}
