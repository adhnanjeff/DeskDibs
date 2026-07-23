package com.deskdibs.booking;

/**
 * The acting user id does not resolve to anybody.
 *
 * <p>Authentication does not exist yet, so the acting user arrives as a parameter and the booking
 * engine has to prove it is real before trusting it. Once a later phase supplies the id from a
 * validated token this stays as the fail-closed backstop.
 */
public class BookingUserNotFoundException extends BookingException {

    private final long userId;

    public BookingUserNotFoundException(long userId) {
        super("No user with id " + userId);
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.USER_NOT_FOUND;
    }
}
