package com.deskdibs.seat;

import java.util.List;

/** A floor of the office and its zones. Multi-floor costs nothing here even though today there is one. */
public record FloorMapView(
        long floorId,
        String name,
        List<ZoneMapView> zones) {
}
