package com.deskdibs.booking;

/**
 * Object-level authorization refusal: this booking is not yours to touch.
 *
 * <p>The check is per-object, not per-endpoint — <em>does this user own this booking</em> — and it
 * is the same check whether the caller is an employee, a manager, or an admin. Nothing here reads
 * a role claim off a token; the acting user's stored role and the stored team-manager relationship
 * are the only inputs.
 */
public class BookingAccessDeniedException extends BookingException {

    /** What was attempted, so a later phase can word the refusal without re-deriving it. */
    public enum Action {
        CANCEL,
        CHECK_IN
    }

    private final long bookingId;
    private final long actingUserId;
    private final long ownerUserId;
    private final Action action;

    public BookingAccessDeniedException(long bookingId, long actingUserId, long ownerUserId, Action action) {
        super("User " + actingUserId + " may not " + action + " booking " + bookingId
                + " owned by user " + ownerUserId);
        this.bookingId = bookingId;
        this.actingUserId = actingUserId;
        this.ownerUserId = ownerUserId;
        this.action = action;
    }

    public long getBookingId() {
        return bookingId;
    }

    public long getActingUserId() {
        return actingUserId;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public Action getAction() {
        return action;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.BOOKING_ACCESS_DENIED;
    }
}
