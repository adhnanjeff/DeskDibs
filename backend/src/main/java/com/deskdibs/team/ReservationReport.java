package com.deskdibs.team;

import java.time.LocalDate;
import java.util.List;

/**
 * The partial-success outcome of {@code POST /api/reservations}, per PLAN.md §4/§7: which seats are
 * now held for the team, and which were left exactly as they were because somebody already holds an
 * ACTIVE booking on at least one day in the requested range. The system never force-cancels a
 * booking to make room for a hold, so an unavailable seat here is not an error — it is reported so
 * the manager can act on it, naming who holds it and on which day.
 */
public record ReservationReport(
        long teamId,
        String teamName,
        LocalDate startDate,
        LocalDate endDate,
        List<HeldSeat> held,
        List<UnavailableSeat> unavailable) {

    /** One seat now reserved for the team across the whole requested range. */
    public record HeldSeat(long reservationId, long seatId, String seatLabel) {
    }

    /** One seat left untouched, naming the earliest day it conflicts and who holds it that day. */
    public record UnavailableSeat(long seatId, String seatLabel, LocalDate conflictingDate,
                                  long conflictingUserId, String conflictingUserDisplayName) {
    }
}
