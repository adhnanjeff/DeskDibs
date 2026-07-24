package com.deskdibs.team;

import com.deskdibs.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Team seat holds over HTTP: MANAGER or ADMIN only.
 *
 * <p>{@code @PreAuthorize} proves the role; it is the database-sourced role on
 * {@code AuthenticatedUser}, never a claim read from the client's token — see
 * {@code AuthenticatedUser#authorities()}. No business logic lives here: validate, resolve the
 * caller, delegate to {@link ReservationService}, map the result.
 */
@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Manager/admin team seat holds.")
public class ReservationController {

    private final ReservationService reservationService;
    private final CurrentUser currentUser;

    public ReservationController(ReservationService reservationService, CurrentUser currentUser) {
        this.reservationService = reservationService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Hold seats for a team over a date range",
            description = "Never force-cancels a booking. Returns a partial-success report naming which "
                    + "seats were held and which were left alone because somebody already holds them, and on "
                    + "which day.")
    @ApiResponse(responseCode = "200", description = "The partial-success report.")
    @ApiResponse(responseCode = "403", description = "The caller is neither a manager nor an admin.")
    @ApiResponse(responseCode = "404", description = "The team, or one of the seat ids, does not exist.")
    public ReservationReport create(@Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(currentUser.requireId(), request.teamId(), request.seatIds(),
                request.startDate(), request.endDate(), request.releaseAtTime());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Release a hold early",
            description = "The hold's creator, the manager of the team it is for, or an admin may release it.")
    @ApiResponse(responseCode = "204", description = "Released.")
    @ApiResponse(responseCode = "403", description = "The caller may not release this hold.")
    @ApiResponse(responseCode = "404", description = "No reservation with that id.")
    public ResponseEntity<Void> release(@PathVariable long id) {
        reservationService.release(id, currentUser.requireId());
        return ResponseEntity.noContent().build();
    }
}
