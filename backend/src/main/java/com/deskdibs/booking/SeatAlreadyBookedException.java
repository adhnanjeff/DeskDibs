package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * You lost the race for this seat.
 *
 * <p>Raised only by translating a {@code uq_seat_active_per_date} violation — the database
 * decided, not a pre-flight availability check. The winner is looked up <em>after</em> the losing
 * insert has failed, so {@link #getTakenByDisplayName()} names whoever actually holds the seat
 * rather than whoever happened to hold it when the loser last read.
 *
 * <p>The winner fields are nullable on purpose: by the time the loser asks, the winner may already
 * have cancelled. A refusal with an unknown holder is still a correct refusal of that insert.
 */
public class SeatAlreadyBookedException extends BookingException {

    private final long seatId;
    private final String seatLabel;
    private final LocalDate bookingDate;
    private final Long takenByUserId;
    private final String takenByDisplayName;

    public SeatAlreadyBookedException(long seatId,
                                      String seatLabel,
                                      LocalDate bookingDate,
                                      Long takenByUserId,
                                      String takenByDisplayName,
                                      Throwable cause) {
        super("Seat " + seatLabel + " is already booked on " + bookingDate
                + (takenByDisplayName == null ? "" : " by " + takenByDisplayName), cause);
        this.seatId = seatId;
        this.seatLabel = seatLabel;
        this.bookingDate = bookingDate;
        this.takenByUserId = takenByUserId;
        this.takenByDisplayName = takenByDisplayName;
    }

    public long getSeatId() {
        return seatId;
    }

    public String getSeatLabel() {
        return seatLabel;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    /** Id of the person who won the seat; {@code null} if the winning booking has since gone. */
    public Long getTakenByUserId() {
        return takenByUserId;
    }

    /** Display name of the person who won the seat, for <em>"Alice grabbed that one a second ago"</em>. */
    public String getTakenByDisplayName() {
        return takenByDisplayName;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.SEAT_ALREADY_BOOKED;
    }
}
