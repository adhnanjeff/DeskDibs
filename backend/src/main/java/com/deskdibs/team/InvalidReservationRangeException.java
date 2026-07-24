package com.deskdibs.team;

import java.time.LocalDate;

/**
 * The requested end date is before the start date.
 *
 * <p>Caught here rather than left to {@code ck_seat_reservation_dates}: the same rule the database
 * enforces, checked before any insert is attempted, so the refusal is a clean 400 rather than a
 * {@code DataIntegrityViolationException} translated from a constraint nothing else in this feature
 * expects to hit.
 */
public class InvalidReservationRangeException extends ReservationException {

    private final LocalDate startDate;
    private final LocalDate endDate;

    public InvalidReservationRangeException(LocalDate startDate, LocalDate endDate) {
        super("Reservation end date " + endDate + " is before start date " + startDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    @Override
    public ReservationErrorCode errorCode() {
        return ReservationErrorCode.INVALID_RESERVATION_RANGE;
    }
}
