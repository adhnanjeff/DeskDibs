package com.deskdibs.team;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Body of {@code POST /api/reservations}.
 *
 * @param releaseAtTime optional; defaults to {@code deskdibs.office.team-block-release-time} when
 *                       omitted, matching {@code seat_reservation.release_at_time}'s own default
 */
public record CreateReservationRequest(

        @NotNull(message = "teamId is required")
        @Schema(example = "3")
        Long teamId,

        @NotEmpty(message = "seatIds must not be empty")
        @Schema(description = "Seats to hold for the team.")
        List<@NotNull(message = "seatIds must not contain null") Long> seatIds,

        @NotNull(message = "startDate is required")
        @Schema(example = "2026-08-10")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        @Schema(example = "2026-08-12")
        LocalDate endDate,

        @Schema(description = "Defaults to the configured team-block release time when omitted.", example = "10:00")
        LocalTime releaseAtTime) {
}
