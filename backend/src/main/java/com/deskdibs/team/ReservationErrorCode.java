package com.deskdibs.team;

/**
 * Stable machine-readable identity of a reservation failure — the same kind of contract
 * {@code BookingErrorCode} gives the booking domain, kept as its own enum because reservations are
 * a distinct feature, not a booking. Names are part of the API: add values, never rename or
 * reorder meaning.
 */
public enum ReservationErrorCode {

    /** No team with that id. */
    TEAM_NOT_FOUND,

    /** One of the requested seat ids does not exist. */
    SEAT_NOT_FOUND,

    /** The requested end date is before the start date. */
    INVALID_RESERVATION_RANGE,

    /** No reservation with that id. */
    RESERVATION_NOT_FOUND,

    /** The acting user did not create this hold, does not manage the team it is for, and is not an admin. */
    RESERVATION_ACCESS_DENIED
}
