package com.deskdibs.booking;

import com.deskdibs.auth.CurrentUser;
import com.deskdibs.common.OfficeClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.SeatMapResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/seatmap} — the endpoint the whole product hangs off.
 *
 * <p>Validates nothing beyond what Spring's own parameter binding already refuses (a malformed
 * {@code date} is a 400 via {@code AuthExceptionHandler#handleTypeMismatch}), resolves "today" from
 * the office clock rather than the caller, and delegates every other decision to
 * {@link SeatMapService}.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Seat map", description = "The top-down floor map the rest of the product hangs off.")
public class SeatMapController {

    private final SeatMapService seatMapService;
    private final OfficeClock officeClock;
    private final OfficeProperties office;
    private final CurrentUser currentUser;

    public SeatMapController(SeatMapService seatMapService,
                             OfficeClock officeClock,
                             OfficeProperties office,
                             CurrentUser currentUser) {
        this.seatMapService = seatMapService;
        this.officeClock = officeClock;
        this.office = office;
        this.currentUser = currentUser;
    }

    @GetMapping("/seatmap")
    @Operation(
            summary = "Get the floor/zone/table/seat layout with each seat's state for a date",
            description = """
                    Defaults to today (office clock) when date is omitted. One HTTP round trip: every \
                    seat's booking and team-hold state for the date is resolved in a bounded number of \
                    queries, never one per seat, so the response scales with the floor plan, not with \
                    how many seats happen to be booked.""")
    @ApiResponse(responseCode = "200", description = "The floor map for the requested date.")
    public SeatMapResponse seatmap(
            @Parameter(description = "ISO date (yyyy-MM-dd). Defaults to today in the office timezone.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return seatMapService.buildMap(date == null ? officeClock.today() : date);
    }

    @GetMapping("/seatmap/horizon")
    @Operation(
            summary = "How full each bookable day is, for the date strip",
            description = """
                    Today through the configured booking horizon, one entry per day: how many desks \
                    are in service, how many are claimed, and which desk you already hold that day. \
                    Answered in three queries for the whole range rather than one seat map per day.""")
    @ApiResponse(responseCode = "200", description = "One entry per bookable day, today first.")
    public List<DayAvailabilityView> horizon() {
        LocalDate today = officeClock.today();
        return seatMapService.availabilityHorizon(
                currentUser.requireId(), today, today.plusDays(office.bookingHorizonDays()));
    }
}
