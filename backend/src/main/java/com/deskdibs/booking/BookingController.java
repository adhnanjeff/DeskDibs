package com.deskdibs.booking;

import com.deskdibs.auth.CurrentUser;
import com.deskdibs.common.OfficeClock;
import com.deskdibs.common.OfficeProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The booking domain over HTTP: claim, move, cancel, check in, and the caller's own bookings.
 *
 * <p>No business logic lives here. Every method validates its input, resolves the caller from
 * {@link CurrentUser}, delegates to {@link BookingService} with exactly the parameters it already
 * takes, and maps the result to {@link BookingResponse}. Every refusal {@code BookingService}
 * throws is mapped to HTTP by {@code AuthExceptionHandler}, not here.
 */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Claim, move, cancel and check in to a seat.")
public class BookingController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final BookingService bookingService;
    private final CurrentUser currentUser;
    private final OfficeClock officeClock;
    private final OfficeProperties office;

    public BookingController(BookingService bookingService,
                             CurrentUser currentUser,
                             OfficeClock officeClock,
                             OfficeProperties office) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
        this.officeClock = officeClock;
        this.office = office;
    }

    @PostMapping
    @Operation(summary = "Claim a seat for a date",
            description = "Replaying the same Idempotency-Key returns the original booking instead of a 409.")
    @ApiResponse(responseCode = "201", description = "The seat is now held by the caller.")
    @ApiResponse(responseCode = "409", description = "Already booked (by someone else, or by the caller elsewhere).")
    @ApiResponse(responseCode = "403", description = "The seat is held for a team the caller is not in.")
    @ApiResponse(responseCode = "400", description = "The date is outside the booking window.")
    public ResponseEntity<BookingResponse> claim(
            @Valid @RequestBody ClaimBookingRequest request,
            @Parameter(description = "Client-generated key; a repeat returns the original booking.")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        BookingView booking = bookingService.claim(currentUser.requireId(), request.seatId(), request.date(),
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.of(booking));
    }

    @PostMapping("/move")
    @Operation(summary = "Move to a different seat on a date",
            description = "Atomically cancels whatever the caller holds that day and claims the new seat. "
                    + "A target seat that cannot be claimed leaves the original booking untouched and ACTIVE.")
    @ApiResponse(responseCode = "200", description = "Now holding the new seat.")
    public ResponseEntity<BookingResponse> move(
            @Valid @RequestBody MoveBookingRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        BookingView booking = bookingService.move(currentUser.requireId(), request.seatId(), request.date(),
                idempotencyKey);
        return ResponseEntity.ok(BookingResponse.of(booking));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a booking",
            description = "The owner, the manager of a team the owner belongs to, or an admin may cancel.")
    @ApiResponse(responseCode = "200", description = "The booking is now CANCELLED.")
    @ApiResponse(responseCode = "403", description = "The caller does not own this booking.")
    @ApiResponse(responseCode = "404", description = "No booking with that id.")
    public ResponseEntity<BookingResponse> cancel(@PathVariable long id) {
        BookingView cancelled = bookingService.cancel(id, currentUser.requireId());
        return ResponseEntity.ok(BookingResponse.of(cancelled));
    }

    @PostMapping("/{id}/check-in")
    @Operation(summary = "Check in to today's booking",
            description = "Only the owner may check in, and only for today's booking.")
    @ApiResponse(responseCode = "200", description = "Checked in.")
    public ResponseEntity<BookingResponse> checkIn(@PathVariable long id) {
        BookingView checkedIn = bookingService.checkIn(id, currentUser.requireId());
        return ResponseEntity.ok(BookingResponse.of(checkedIn));
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's own bookings in a date range",
            description = "Defaults to today through the end of the booking window when from/to are omitted.")
    @ApiResponse(responseCode = "200", description = "The caller's bookings in the range, earliest first.")
    public List<BookingResponse> mine(
            @Parameter(description = "Defaults to today in the office timezone.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Defaults to the end of the booking window.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate resolvedFrom = from == null ? officeClock.today() : from;
        LocalDate resolvedTo = to == null ? officeClock.today().plusDays(office.bookingHorizonDays()) : to;

        return bookingService.findMine(currentUser.requireId(), resolvedFrom, resolvedTo).stream()
                .map(BookingResponse::of)
                .toList();
    }
}
