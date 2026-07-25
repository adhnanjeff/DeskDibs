package com.deskdibs.team;

import com.deskdibs.seat.SeatReservation;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One live hold, as the manager UI sees it.
 *
 * <p>Carries the reservation id, which is the piece the seat map cannot supply: the map reports
 * that a seat is held and by which team, but not the identity of the hold itself — so without this
 * view a manager could see their block and have no way to release it.
 *
 * @param enforcedNow whether the hold is actually holding its seat at this moment. Team blocks
 *                    release softly: past {@code releaseAtTime} on a given day the hold simply
 *                    stops being enforced, with no job and no state change, so a row can exist and
 *                    hold nothing. Answered server-side against the office clock, because it is the
 *                    difference between "your block is live" and "anyone can sit there now" — and
 *                    without it the manager sees a hold in this list and no hold on the map, which
 *                    looks broken rather than intended.
 */
public record ReservationView(
        long id,
        long seatId,
        String seatLabel,
        long teamId,
        String teamName,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime releaseAtTime,
        String createdByName,
        boolean enforcedNow) {

    static ReservationView of(SeatReservation reservation, boolean enforcedNow) {
        return new ReservationView(
                reservation.getId(),
                reservation.getSeat().getId(),
                reservation.getSeat().getLabel(),
                reservation.getTeam().getId(),
                reservation.getTeam().getName(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getReleaseAtTime(),
                reservation.getCreatedBy() == null ? null : reservation.getCreatedBy().getDisplayName(),
                enforcedNow);
    }
}
