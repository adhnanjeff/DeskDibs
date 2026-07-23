package com.deskdibs.booking;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A team hold still covers this seat on this date, and the claimant is not in that team.
 *
 * <p>Carries the team name and the release time so the caller can say <em>"held for Platform
 * until 10:00"</em> — the two facts that turn a refusal into something a person can act on.
 * The hold is soft: after {@link #getReleaseAtTime()} on {@link #getBookingDate()} the same claim
 * succeeds, with no job and no state change in between.
 */
public class SeatReservedForTeamException extends BookingException {

    private final long seatId;
    private final String seatLabel;
    private final LocalDate bookingDate;
    private final long teamId;
    private final String teamName;
    private final LocalTime releaseAtTime;

    public SeatReservedForTeamException(long seatId,
                                        String seatLabel,
                                        LocalDate bookingDate,
                                        long teamId,
                                        String teamName,
                                        LocalTime releaseAtTime) {
        super("Seat " + seatLabel + " is held for team " + teamName + " on " + bookingDate
                + " until " + releaseAtTime);
        this.seatId = seatId;
        this.seatLabel = seatLabel;
        this.bookingDate = bookingDate;
        this.teamId = teamId;
        this.teamName = teamName;
        this.releaseAtTime = releaseAtTime;
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

    public long getTeamId() {
        return teamId;
    }

    /** The team the seat is held for, named so the refusal explains itself. */
    public String getTeamName() {
        return teamName;
    }

    /** Office-local time of day at which the hold stops being enforced on {@link #getBookingDate()}. */
    public LocalTime getReleaseAtTime() {
        return releaseAtTime;
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.SEAT_RESERVED_FOR_TEAM;
    }
}
