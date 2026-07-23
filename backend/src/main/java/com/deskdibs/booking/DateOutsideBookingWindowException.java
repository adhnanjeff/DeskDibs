package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * The requested date is in the past or beyond the booking horizon.
 *
 * <p>Carries the whole allowed range, so the caller can say <em>which</em> dates would have
 * worked instead of only that this one did not. Both bounds are resolved from the office clock,
 * never from anything the client sent.
 */
public class DateOutsideBookingWindowException extends BookingException {

    private final LocalDate requestedDate;
    private final LocalDate earliestAllowed;
    private final LocalDate latestAllowed;

    public DateOutsideBookingWindowException(LocalDate requestedDate,
                                             LocalDate earliestAllowed,
                                             LocalDate latestAllowed) {
        super("Booking date " + requestedDate + " is outside the allowed window "
                + earliestAllowed + ".." + latestAllowed);
        this.requestedDate = requestedDate;
        this.earliestAllowed = earliestAllowed;
        this.latestAllowed = latestAllowed;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    /** Today in the office. */
    public LocalDate getEarliestAllowed() {
        return earliestAllowed;
    }

    /** Today plus the configured horizon, inclusive. */
    public LocalDate getLatestAllowed() {
        return latestAllowed;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.DATE_OUTSIDE_BOOKING_WINDOW;
    }
}
