package com.deskdibs.admin;

import com.deskdibs.seat.SeatStatus;
import jakarta.validation.constraints.NotNull;

/** Put a desk into, or take it out of, the bookable pool. */
public record UpdateSeatStatusRequest(@NotNull SeatStatus status) {
}
