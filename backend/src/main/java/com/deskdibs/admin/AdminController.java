package com.deskdibs.admin;

import com.deskdibs.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Administration: account lifecycle and the bookable state of the floor plan.
 *
 * <p>ADMIN only, and unlike {@code ReservationController} there is no manager tier here. A manager
 * holding desks for their own team is a normal operational act; deactivating a colleague's account
 * or withdrawing a desk from the whole office is not, and both cost other people their bookings.
 *
 * <p>{@code @PreAuthorize} resolves {@code hasRole('ADMIN')} against the authority derived from
 * {@code app_user.role} in the database — never a role claim read off the incoming token. No
 * business logic lives here: validate, resolve the caller, delegate, return the report.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administration", description = "Account lifecycle and floor-plan changes. Admins only.")
public class AdminController {

    private final UserAdminService userAdmin;
    private final SeatAdminService seatAdmin;
    private final OccupancyReportService occupancyReports;
    private final CurrentUser currentUser;

    public AdminController(UserAdminService userAdmin,
                           SeatAdminService seatAdmin,
                           OccupancyReportService occupancyReports,
                           CurrentUser currentUser) {
        this.userAdmin = userAdmin;
        this.seatAdmin = seatAdmin;
        this.occupancyReports = occupancyReports;
        this.currentUser = currentUser;
    }

    @GetMapping("/reports/occupancy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Who sat where on one day",
            description = "Every booking made for the date, including ones that were cancelled or taken "
                    + "back by the no-show release \u2014 the seat map only shows live bookings for today, so it "
                    + "cannot answer what actually happened on a past date. Rows are in seat order.")
    @ApiResponse(responseCode = "200", description = "The day's record.")
    @ApiResponse(responseCode = "403", description = "The caller is not an administrator.")
    public DayOccupancyReport occupancyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return occupancyReports.forDate(date);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Everyone on the system",
            description = "Name, email, role and whether the account is live. Never returns credentials.")
    @ApiResponse(responseCode = "200", description = "The people list.")
    @ApiResponse(responseCode = "403", description = "The caller is not an administrator.")
    public List<AdminUserView> users() {
        return userAdmin.listUsers();
    }

    @PatchMapping("/users/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate an account",
            description = "Deactivating refuses the account at login and hands back every desk it still "
                    + "holds from today onward. The response names each booking that was released, so the "
                    + "consequence is visible rather than silent. Reactivating restores access but does not "
                    + "reclaim desks.")
    @ApiResponse(responseCode = "200", description = "What changed, including the bookings released.")
    @ApiResponse(responseCode = "403", description = "The caller is not an administrator.")
    @ApiResponse(responseCode = "404", description = "No user with that id.")
    @ApiResponse(responseCode = "409", description = "An administrator may not deactivate their own account.")
    public UserActivationReport setUserActive(@PathVariable long id,
                                              @Valid @RequestBody UpdateUserActiveRequest request) {
        return userAdmin.setActive(id, request.active(), currentUser.requireId());
    }

    @PatchMapping("/seats/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Take a desk out of the pool, or put it back",
            description = "DISABLED or BROKEN withdraws the desk and releases every booking on it from "
                    + "today onward; the affected people see the reason on their own bookings page. ACTIVE "
                    + "returns it to the pool. The seat row is never deleted — booking history survives a "
                    + "floor-plan change.")
    @ApiResponse(responseCode = "200", description = "What changed, including the bookings released.")
    @ApiResponse(responseCode = "403", description = "The caller is not an administrator.")
    @ApiResponse(responseCode = "404", description = "No seat with that id.")
    public SeatStatusChangeReport setSeatStatus(@PathVariable long id,
                                                @Valid @RequestBody UpdateSeatStatusRequest request) {
        return seatAdmin.setStatus(id, request.status());
    }
}
