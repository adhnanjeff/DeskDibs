package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * You already hold a seat that day.
 *
 * <p>Raised by translating a {@code uq_user_active_per_date} violation. Carries the seat you
 * already have, which is what turns the refusal into the offer the product actually wants:
 * <em>"You have R2-A3 that day. Move here instead?"</em> — see {@code BookingService#move}.
 *
 * <p>The existing-booking fields are nullable: the row may have been cancelled between the failed
 * insert and the lookup.
 */
public class AlreadyBookedThatDayException extends BookingException {

    private final long userId;
    private final LocalDate bookingDate;
    private final Long existingBookingId;
    private final Long existingSeatId;
    private final String existingSeatLabel;

    public AlreadyBookedThatDayException(long userId,
                                         LocalDate bookingDate,
                                         Long existingBookingId,
                                         Long existingSeatId,
                                         String existingSeatLabel,
                                         Throwable cause) {
        super("User " + userId + " already holds "
                + (existingSeatLabel == null ? "a seat" : existingSeatLabel) + " on " + bookingDate, cause);
        this.userId = userId;
        this.bookingDate = bookingDate;
        this.existingBookingId = existingBookingId;
        this.existingSeatId = existingSeatId;
        this.existingSeatLabel = existingSeatLabel;
    }

    public long getUserId() {
        return userId;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public Long getExistingBookingId() {
        return existingBookingId;
    }

    public Long getExistingSeatId() {
        return existingSeatId;
    }

    /** Label of the seat the caller already holds that day, e.g. {@code R2-A3}. */
    public String getExistingSeatLabel() {
        return existingSeatLabel;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.ALREADY_BOOKED_THAT_DAY;
    }
}
