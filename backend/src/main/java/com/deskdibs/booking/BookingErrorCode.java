package com.deskdibs.booking;

/**
 * Stable machine-readable identity of a booking failure.
 *
 * <p>The wire contract of a later phase is built from these, so a client can branch on
 * {@code SEAT_ALREADY_BOOKED} without parsing an English sentence. Names are part of the API:
 * add values, never rename or reorder meaning.
 */
public enum BookingErrorCode {

    /** Requested date is before today or past the booking horizon. */
    DATE_OUTSIDE_BOOKING_WINDOW,

    /** The seat exists but is DISABLED or BROKEN. */
    SEAT_NOT_BOOKABLE,

    /** A team hold still covers the seat and the claimant is not in that team. */
    SEAT_RESERVED_FOR_TEAM,

    /** Somebody else's ACTIVE booking already holds the seat for that date. */
    SEAT_ALREADY_BOOKED,

    /** The claimant already holds a different seat on that date. */
    ALREADY_BOOKED_THAT_DAY,

    /** The idempotency key was first used for a materially different request. */
    IDEMPOTENCY_KEY_CONFLICT,

    /** No booking with that id. */
    BOOKING_NOT_FOUND,

    /** The booking is CANCELLED or RELEASED_NO_SHOW, so it can no longer be operated on. */
    BOOKING_NOT_ACTIVE,

    /** The acting user is not allowed to operate on somebody else's booking. */
    BOOKING_ACCESS_DENIED,

    /** Check-in attempted for a booking that is not for today. */
    CHECK_IN_NOT_FOR_TODAY,

    /** Already checked in. */
    ALREADY_CHECKED_IN,

    /** No seat with that id. */
    SEAT_NOT_FOUND,

    /** No user with that id. */
    USER_NOT_FOUND
}
