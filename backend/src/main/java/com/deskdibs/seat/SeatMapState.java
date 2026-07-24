package com.deskdibs.seat;

/**
 * What the seat map renders a seat as, for one date.
 *
 * <p>Deliberately smaller than the full cross-product of {@code SeatStatus} and booking/reservation
 * state: a {@code DISABLED} or {@code BROKEN} seat is reported as {@link #DISABLED} regardless of
 * whether it happens to also carry a stale booking or hold, because the map only needs to know it
 * cannot be interacted with. Per CLAUDE.md's UI standard, this is never the only signal the frontend
 * renders — colour, shape and icon travel together — but it is the one authoritative fact this API
 * hands over for a client to key that rendering off.
 */
public enum SeatMapState {

    /** Bookable, unclaimed, and not currently held for a team. */
    AVAILABLE,

    /** An ACTIVE booking covers this seat on this date; see {@code occupantUserId}. */
    OCCUPIED,

    /** A team hold still covers this seat on this date, and nobody has claimed it. */
    TEAM_RESERVED,

    /** The seat itself is DISABLED or BROKEN, independent of any booking or hold. */
    DISABLED
}
