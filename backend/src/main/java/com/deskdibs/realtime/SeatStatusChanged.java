package com.deskdibs.realtime;

import com.deskdibs.seat.SeatMapView;

import java.time.LocalDate;

/**
 * The payload broadcast to {@code /topic/seatmap/{date}} on every claim, move, cancel and check-in,
 * per PLAN.md §6. Wraps a {@link SeatMapView} — the exact same shape {@code GET /api/seatmap} uses
 * for that seat — so a client applies a live update with the same rendering code it uses for the
 * initial snapshot.
 */
public record SeatStatusChanged(LocalDate bookingDate, SeatMapView seat) {
}
