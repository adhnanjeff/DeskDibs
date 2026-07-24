package com.deskdibs.seat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

/**
 * One seat's state on one date — the leaf of {@code GET /api/seatmap}'s floor/zone/table/seat
 * hierarchy, and also the payload {@code SeatStatusChanged} broadcasts over {@code /topic/seatmap/
 * {date}}. The two uses share this exact shape deliberately: a client that renders the map from one
 * can apply a live update from the other with the same code path.
 *
 * <p>Every field beyond {@code seatId}/{@code seatLabel}/{@code side}/{@code seatIndex}/
 * {@code accessible}/{@code state} is nullable and only meaningful for the state it belongs to —
 * {@code occupant*}/{@code bookingId}/{@code checkedIn} for {@link SeatMapState#OCCUPIED},
 * {@code reservedFor*} for {@link SeatMapState#TEAM_RESERVED}. Whether the occupant is the caller is
 * deliberately not a field here: the client already knows its own user id from {@code GET
 * /api/auth/me} and can compare it to {@code occupantUserId} itself, which keeps this DTO identical
 * for every viewer and safe to broadcast to every subscriber of a date's topic.
 *
 * <p>The factory methods are {@code public} rather than package-private because the service that
 * builds these — {@code com.deskdibs.booking.SeatMapService} — deliberately lives in the
 * {@code booking} package rather than here: {@code booking} already depends on {@code seat} (a
 * booking is for a seat), so building the map there adds no new dependency direction. Building it
 * here instead would make {@code seat} depend back on {@code booking} for the booking/occupant data
 * every non-{@code AVAILABLE} state needs, turning one clean edge into a cycle.
 */
@Schema(name = "SeatMapSeat")
public record SeatMapView(

        long seatId,
        @Schema(example = "R3-A2") String seatLabel,
        SeatSide side,
        int seatIndex,
        boolean accessible,
        SeatMapState state,

        Long bookingId,
        Long occupantUserId,
        String occupantDisplayName,
        boolean checkedIn,

        Long reservedForTeamId,
        String reservedForTeamName,
        LocalTime reservedUntil) {

    public static SeatMapView disabled(Seat seat) {
        return new SeatMapView(seat.getId(), seat.getLabel(), seat.getSide(), seat.getSeatIndex(),
                seat.isAccessible(), SeatMapState.DISABLED,
                null, null, null, false,
                null, null, null);
    }

    public static SeatMapView available(Seat seat) {
        return new SeatMapView(seat.getId(), seat.getLabel(), seat.getSide(), seat.getSeatIndex(),
                seat.isAccessible(), SeatMapState.AVAILABLE,
                null, null, null, false,
                null, null, null);
    }

    public static SeatMapView occupied(Seat seat, long bookingId, long occupantUserId, String occupantDisplayName,
                                       boolean checkedIn) {
        return new SeatMapView(seat.getId(), seat.getLabel(), seat.getSide(), seat.getSeatIndex(),
                seat.isAccessible(), SeatMapState.OCCUPIED,
                bookingId, occupantUserId, occupantDisplayName, checkedIn,
                null, null, null);
    }

    public static SeatMapView teamReserved(Seat seat, long teamId, String teamName, LocalTime releaseAtTime) {
        return new SeatMapView(seat.getId(), seat.getLabel(), seat.getSide(), seat.getSeatIndex(),
                seat.isAccessible(), SeatMapState.TEAM_RESERVED,
                null, null, null, false,
                teamId, teamName, releaseAtTime);
    }
}
