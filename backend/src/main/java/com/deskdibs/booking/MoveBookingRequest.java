package com.deskdibs.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Body of {@code POST /api/bookings/move}: move to this seat on this date. */
public record MoveBookingRequest(

        @NotNull(message = "seatId is required")
        @Schema(description = "The seat to move to.", example = "43")
        Long seatId,

        @NotNull(message = "date is required")
        @Schema(description = "ISO date within the booking window.", example = "2026-08-10")
        LocalDate date) {
}
