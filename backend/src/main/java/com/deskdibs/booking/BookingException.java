package com.deskdibs.booking;

/**
 * Base of every way the booking engine can refuse a request.
 *
 * <p>Subclasses carry <em>structured</em> data — a seat label, the winner's display name, a team
 * name, an allowed date range — as typed accessors, never only as words inside
 * {@link #getMessage()}. A later phase maps these to HTTP statuses and JSON, and formatting a
 * sentence here would force it to parse one back out. The message exists for logs and stack
 * traces only.
 *
 * <p>No {@code @ResponseStatus} and no other web annotation appears anywhere in this hierarchy:
 * the domain does not know that HTTP exists.
 *
 * <p>Unchecked because every one of these is a refusal the caller cannot meaningfully recover from
 * in-line, and because the transaction must unwind (Spring rolls back on unchecked exceptions).
 */
public abstract class BookingException extends RuntimeException {

    protected BookingException(String message) {
        super(message);
    }

    protected BookingException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Stable identity of this failure, for mapping and for clients to branch on. */
    public abstract BookingErrorCode errorCode();
}
