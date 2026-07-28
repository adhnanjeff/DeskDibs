package com.deskdibs.admin;

/**
 * Stable machine-readable identity of an administrative failure, mirroring
 * {@code BookingErrorCode} and {@code ReservationErrorCode}. Names are part of the API: add
 * values, never rename or reorder meaning.
 */
public enum AdminErrorCode {

    /** No user with that id. */
    ADMIN_USER_NOT_FOUND,

    /** No seat with that id. */
    ADMIN_SEAT_NOT_FOUND,

    /** An administrator tried to deactivate their own account. */
    CANNOT_DEACTIVATE_SELF
}
