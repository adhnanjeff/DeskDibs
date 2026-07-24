package com.deskdibs.seat;

import java.util.List;

/** One desk table and its seats, positioned for the top-down map by {@code posX}/{@code posY}/{@code rotation}. */
public record TableMapView(
        long tableId,
        String label,
        int capacity,
        int posX,
        int posY,
        int rotation,
        List<SeatMapView> seats) {
}
