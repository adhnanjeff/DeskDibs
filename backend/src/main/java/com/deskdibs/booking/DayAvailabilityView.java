package com.deskdibs.booking;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * How full one bookable day is, for the date strip (PLAN.md §7).
 *
 * <p>The point of the strip is to let somebody see which day is quiet <em>before</em> committing to
 * it, so this carries the two numbers that answer that and the one fact that makes the strip
 * personal: whether you already hold a desk that day.
 *
 * @param bookableSeats desks in service that day — the denominator, excluding anything disabled
 * @param bookedSeats   desks already claimed, whether or not the person has checked in
 * @param yourSeatLabel the desk you already hold that day, or {@code null} if you hold none
 * @param bookable      whether the office is open that day. The strip shows closed days rather than
 *                      hiding them — a fortnight with gaps in it is harder to read than one where
 *                      every day is present and the shut ones say so.
 * @param today         whether this entry is the office's own today. Sent rather than inferred from
 *                      the entry's position: past the same-day cut-off the horizon opens on
 *                      tomorrow, so "first in the list" and "today" stop being the same thing, and
 *                      a client has no trustworthy clock of its own to tell them apart with.
 */
public record DayAvailabilityView(
        LocalDate date,
        @Schema(example = "102") int bookableSeats,
        @Schema(example = "43") int bookedSeats,
        @Schema(example = "R5-A1", nullable = true) String yourSeatLabel,
        @Schema(example = "true") boolean bookable,
        @Schema(example = "false") boolean today) {

    /** Free desks that day. */
    public int freeSeats() {
        return Math.max(0, bookableSeats - bookedSeats);
    }
}
