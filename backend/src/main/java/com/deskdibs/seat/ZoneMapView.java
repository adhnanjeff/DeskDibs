package com.deskdibs.seat;

import java.util.List;

/** A named area of a floor — "Left Wing" / "Right Wing" in the interim layout — and its tables. */
public record ZoneMapView(
        long zoneId,
        String name,
        List<TableMapView> tables) {
}
