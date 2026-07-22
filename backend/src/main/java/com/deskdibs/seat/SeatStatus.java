package com.deskdibs.seat;

/** Physical availability of a seat, independent of whether it is booked today. */
public enum SeatStatus {
    /** Bookable. */
    ACTIVE,
    /** Taken out of the pool deliberately (renovation, distancing, storage). */
    DISABLED,
    /** Out of service until it is repaired. */
    BROKEN
}
