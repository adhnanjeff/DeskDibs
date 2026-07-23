package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * The idempotency key has already been used for a materially different request.
 *
 * <p>Replaying a key with the same user, seat and date returns the original booking — that is the
 * whole point of the key. Reusing one key for a <em>different</em> claim is a client bug, and
 * quietly returning the first booking would hand the caller a seat it never asked for. So it is
 * refused, with both the original and the requested claim attached so the bug is obvious.
 */
public class IdempotencyKeyConflictException extends BookingException {

    private final String idempotencyKey;
    private final BookingView originalBooking;
    private final long requestedUserId;
    private final long requestedSeatId;
    private final LocalDate requestedDate;

    public IdempotencyKeyConflictException(String idempotencyKey,
                                           BookingView originalBooking,
                                           long requestedUserId,
                                           long requestedSeatId,
                                           LocalDate requestedDate) {
        super("Idempotency key was first used for booking " + originalBooking.id()
                + " and cannot be reused for a different claim");
        this.idempotencyKey = idempotencyKey;
        this.originalBooking = originalBooking;
        this.requestedUserId = requestedUserId;
        this.requestedSeatId = requestedSeatId;
        this.requestedDate = requestedDate;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** What the key actually created the first time. */
    public BookingView getOriginalBooking() {
        return originalBooking;
    }

    public long getRequestedUserId() {
        return requestedUserId;
    }

    public long getRequestedSeatId() {
        return requestedSeatId;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.IDEMPOTENCY_KEY_CONFLICT;
    }
}
