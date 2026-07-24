package com.deskdibs.team;

/**
 * Object-level authorization refusal: this hold is not this manager's to release.
 *
 * <p>{@code @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")} only proves the caller holds one of
 * those roles — it says nothing about whether they are entitled to touch <em>this</em> hold. That
 * question is answered here, per object: did this user create it, do they manage the team it is
 * for, or are they an admin. Nothing here reads a role claim off a token; the acting user's stored
 * role and the stored team-manager relationship are the only inputs, exactly as
 * {@code BookingService#requireMayAct} already does for bookings.
 */
public class ReservationAccessDeniedException extends ReservationException {

    private final long reservationId;
    private final long actingUserId;

    public ReservationAccessDeniedException(long reservationId, long actingUserId) {
        super("User " + actingUserId + " may not release reservation " + reservationId);
        this.reservationId = reservationId;
        this.actingUserId = actingUserId;
    }

    public long getReservationId() {
        return reservationId;
    }

    public long getActingUserId() {
        return actingUserId;
    }

    @Override
    public ReservationErrorCode errorCode() {
        return ReservationErrorCode.RESERVATION_ACCESS_DENIED;
    }
}
