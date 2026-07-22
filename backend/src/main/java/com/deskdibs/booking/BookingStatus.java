package com.deskdibs.booking;

/**
 * Lifecycle of a booking.
 *
 * <p>Only {@link #ACTIVE} rows participate in the partial unique indexes, which is what makes a
 * cancelled or auto-released seat immediately claimable again without deleting history.
 */
public enum BookingStatus {
    /** Holds the seat. Exactly one of these per seat per date, and per person per date. */
    ACTIVE,
    /** Given up by the owner (or superseded by a move). */
    CANCELLED,
    /** Nobody checked in by the cut-off, so the seat went back into the pool. */
    RELEASED_NO_SHOW
}
