package com.deskdibs.admin;

import com.deskdibs.seat.SeatStatus;

import java.util.List;

/**
 * What actually happened when a desk was taken out of, or put back into, the pool.
 *
 * <p>Carries the people whose bookings it cost, for the same reason {@link UserActivationReport}
 * does: withdrawing a desk is not a neutral edit to a floor plan when somebody has already planned
 * their week around sitting at it.
 *
 * @param wasAlreadyInThatState the seat was already in the requested status, so nothing changed
 */
public record SeatStatusChangeReport(
        Long seatId,
        String seatLabel,
        SeatStatus previousStatus,
        SeatStatus status,
        boolean wasAlreadyInThatState,
        int bookingsReleased,
        List<ReleasedBookingView> released) {
}
