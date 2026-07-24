package com.deskdibs.team;

/**
 * Base of every way team-reservation creation or release can be refused.
 *
 * <p>Mirrors {@code com.deskdibs.booking.BookingException} deliberately: subclasses carry
 * structured data as typed accessors, never only as words inside {@link #getMessage()}; no
 * {@code @ResponseStatus} or other web annotation appears anywhere in this hierarchy, because the
 * domain does not know HTTP exists; and it is unchecked because every one of these refusals unwinds
 * the transaction rather than being recovered from in-line.
 */
public abstract class ReservationException extends RuntimeException {

    protected ReservationException(String message) {
        super(message);
    }

    /** Stable identity of this failure, for mapping and for clients to branch on. */
    public abstract ReservationErrorCode errorCode();
}
