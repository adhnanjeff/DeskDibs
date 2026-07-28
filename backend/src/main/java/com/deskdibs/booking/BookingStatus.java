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
    RELEASED_NO_SHOW,
    /**
     * The owner's account was deactivated, so the desk they were still holding went back into the
     * pool (PLAN.md §5 #12). Deliberately not {@link #CANCELLED}: nobody gave this up, and somebody
     * reading their own history should be able to tell the difference.
     */
    RELEASED_USER_DEACTIVATED,
    /**
     * The seat left the bookable floor plan — disabled or broken — so the booking could not stand
     * (PLAN.md §5 #13). The person is told, and has to pick another desk.
     */
    RELEASED_SEAT_REMOVED;

    /** True when this booking still holds its seat. Only {@link #ACTIVE} does. */
    public boolean holdsTheSeat() {
        return this == ACTIVE;
    }
}
