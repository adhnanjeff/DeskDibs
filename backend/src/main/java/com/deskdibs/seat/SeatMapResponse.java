package com.deskdibs.seat;

import java.time.LocalDate;
import java.util.List;

/**
 * The whole top-down floor map for one date — the endpoint the rest of the product hangs off. One
 * HTTP round trip, built from exactly three bounded queries regardless of how many seats, bookings
 * or holds exist: the layout ({@link SeatRepository#findAllWithLayoutOrdered()}), the day's
 * bookings ({@code BookingRepository#findByBookingDateAndStatusFetchUser}), and the day's holds
 * ({@code SeatReservationRepository#findActiveOnDateFetchTeam}).
 */
public record SeatMapResponse(
        LocalDate date,
        List<FloorMapView> floors) {
}
