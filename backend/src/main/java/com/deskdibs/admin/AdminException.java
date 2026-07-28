package com.deskdibs.admin;

/**
 * Base of every way an administrative action can be refused.
 *
 * <p>Mirrors {@code com.deskdibs.booking.BookingException} and
 * {@code com.deskdibs.team.ReservationException}: subclasses carry structured data as typed
 * accessors rather than only as words inside {@link #getMessage()}, no web annotation appears
 * anywhere in the hierarchy because the domain does not know HTTP exists, and it is unchecked
 * because each of these unwinds the transaction rather than being recovered from in-line.
 */
public abstract class AdminException extends RuntimeException {

    protected AdminException(String message) {
        super(message);
    }

    /** Stable identity of this failure, for mapping and for clients to branch on. */
    public abstract AdminErrorCode errorCode();
}
