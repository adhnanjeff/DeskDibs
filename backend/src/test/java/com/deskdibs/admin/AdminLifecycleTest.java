package com.deskdibs.admin;

import com.deskdibs.auth.AbstractAuthWebTest;
import com.deskdibs.booking.Booking;
import com.deskdibs.booking.BookingRepository;
import com.deskdibs.booking.BookingService;
import com.deskdibs.booking.BookingStatus;
import com.deskdibs.booking.SeatNotBookableException;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatStatus;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two administrative edge cases from PLAN.md §5, through the real HTTP stack.
 *
 * <p><b>#12 — an employee leaves.</b> Their account is refused at login <em>and</em> the desks they
 * were still holding go back into the pool. The second half is the one that was missing: without it
 * a departed colleague keeps a desk booked every working day for the next fortnight, it renders as
 * occupied, and nobody can either take it or work out why.
 *
 * <p><b>#13 — the floor plan changes.</b> A desk leaves the pool by changing status, never by being
 * deleted, so booking history survives a refit. Whoever loses a desk finds out because the booking
 * is flipped to {@code RELEASED_SEAT_REMOVED} rather than quietly cancelled.
 *
 * <p>Both are irreversible for the people they affect, so the tests assert on what happened to
 * <em>other people's</em> rows, not merely on the response code.
 */
@Import(ControllableClockConfiguration.class)
class AdminLifecycleTest extends AbstractAuthWebTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final String PASSWORD = "correct horse battery staple";

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final BookingService bookingService;
    private final PasswordEncoder passwordEncoder;
    private final MutableClock clock;
    private final ZoneId office;

    private long root;
    private long dana;
    private long erin;
    private long leaver;

    AdminLifecycleTest(MockMvc mockMvc,
                       ObjectMapper json,
                       AppUserRepository users,
                       BookingRepository bookings,
                       SeatRepository seats,
                       BookingService bookingService,
                       PasswordEncoder passwordEncoder,
                       MutableClock clock,
                       OfficeProperties officeProperties) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.users = users;
        this.bookings = bookings;
        this.seats = seats;
        this.bookingService = bookingService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.office = officeProperties.timezone();
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        clock.setTo(ZonedDateTime.of(TODAY, ControllableClockConfiguration.DEFAULT_TIME_OF_DAY, office));
        clearEverything();
        restoreEverySeat();

        root = person("root@deskdibs.test", "Root A.", UserRole.ADMIN);
        dana = person("dana@deskdibs.test", "Dana K.", UserRole.MANAGER);
        erin = person("erin@deskdibs.test", "Erin Employee", UserRole.EMPLOYEE);
        leaver = person("leaver@deskdibs.test", "Lee Aver", UserRole.EMPLOYEE);
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearEverything();
        restoreEverySeat();
    }

    // ─── Who may administer ──────────────────────────────────────────────────────

    @Test
    @DisplayName("neither an employee nor a manager may reach the administration endpoints")
    void administrationIsAdminOnly() throws Exception {
        mockMvc.perform(authed(get("/api/admin/users"), erin)).andExpect(status().isForbidden());

        // A manager is trusted to hold desks for their own team. Deactivating a colleague's account
        // or withdrawing a desk from the whole office is a different order of thing, so MANAGER —
        // which ReservationController does accept — is deliberately not enough here.
        mockMvc.perform(authed(get("/api/admin/users"), dana)).andExpect(status().isForbidden());
        mockMvc.perform(deactivate(dana, leaver)).andExpect(status().isForbidden());

        mockMvc.perform(authed(get("/api/admin/users"), root)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the people list is ordered by name and never carries a credential")
    void thePeopleListNeverCarriesACredential() throws Exception {
        mockMvc.perform(authed(get("/api/admin/users"), root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                // Ordered by display name, so the row an administrator is about to click does not
                // move between refreshes: Dana K. · Erin Employee · Lee Aver · Root A.
                .andExpect(jsonPath("$[2].displayName").value("Lee Aver"))
                .andExpect(jsonPath("$[2].email").value("leaver@deskdibs.test"))
                .andExpect(jsonPath("$[2].active").value(true))
                .andExpect(jsonPath("$[2].role").value("EMPLOYEE"))
                // A credential hash has no business leaving the persistence layer, and the Entra
                // object id is free reconnaissance.
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].externalId").doesNotExist());
    }

    // ─── §5 #12 — an employee leaves ─────────────────────────────────────────────

    @Test
    @DisplayName("deactivating somebody hands back every desk they were still holding")
    void deactivationReleasesTheDesksTheyWereHolding() throws Exception {
        Booking todays = book(leaver, "R4-A1", TODAY);
        Booking nextWeeks = book(leaver, "R4-A2", TODAY.plusDays(7));

        mockMvc.perform(deactivate(root, leaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.bookingsReleased").value(2))
                // The report names them, so an administrator sees the cost of what they just did
                // rather than a bare "done".
                .andExpect(jsonPath("$.released.length()").value(2))
                .andExpect(jsonPath("$.released[0].seatLabel").value("R4-A1"))
                .andExpect(jsonPath("$.released[0].userDisplayName").value("Lee Aver"));

        assertThat(statusOf(todays))
                .as("today counts: somebody who has left is not coming in this afternoon either")
                .isEqualTo(BookingStatus.RELEASED_USER_DEACTIVATED);
        assertThat(statusOf(nextWeeks)).isEqualTo(BookingStatus.RELEASED_USER_DEACTIVATED);
    }

    @Test
    @DisplayName("a desk freed by a deactivation is immediately claimable by somebody else")
    void aFreedDeskCanBeClaimedByTheNextPerson() throws Exception {
        book(leaver, "R4-A1", TODAY);
        mockMvc.perform(deactivate(root, leaver)).andExpect(status().isOk());

        // The whole point of the release. RELEASED_USER_DEACTIVATED is not ACTIVE, so the row drops
        // out of uq_seat_active_per_date and the seat is simply free again.
        assertThat(bookingService.claim(erin, seatId("R4-A1"), TODAY, null).seatLabel())
                .isEqualTo("R4-A1");
    }

    @Test
    @DisplayName("deactivation leaves history alone — only what is still held is released")
    void deactivationLeavesPastBookingsAlone() throws Exception {
        Booking lastWeek = book(leaver, "R4-A1", TODAY.minusDays(5));

        mockMvc.perform(deactivate(root, leaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsReleased").value(0));

        assertThat(statusOf(lastWeek))
                .as("rewriting a day somebody actually came in would falsify attendance history")
                .isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    @DisplayName("a deactivated account is refused at login")
    void aDeactivatedAccountCannotSignIn() throws Exception {
        mockMvc.perform(deactivate(root, leaver)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "leaver@deskdibs.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("reactivating restores access but does not reclaim the desks")
    void reactivationDoesNotReclaimDesks() throws Exception {
        Booking nextWeek = book(leaver, "R4-A2", TODAY.plusDays(7));
        mockMvc.perform(deactivate(root, leaver)).andExpect(status().isOk());

        mockMvc.perform(setActive(root, leaver, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.bookingsReleased").value(0));

        assertThat(statusOf(nextWeek))
                .as("somebody else may be sitting there by now — silently re-claiming would be worse "
                        + "than making them book again")
                .isEqualTo(BookingStatus.RELEASED_USER_DEACTIVATED);
    }

    @Test
    @DisplayName("deactivating an already-inactive account releases nothing a second time")
    void deactivationIsIdempotent() throws Exception {
        book(leaver, "R4-A1", TODAY);
        mockMvc.perform(deactivate(root, leaver))
                .andExpect(jsonPath("$.bookingsReleased").value(1))
                .andExpect(jsonPath("$.wasAlreadyInThatState").value(false));

        mockMvc.perform(deactivate(root, leaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasAlreadyInThatState").value(true))
                .andExpect(jsonPath("$.bookingsReleased").value(0));
    }

    @Test
    @DisplayName("an administrator may not deactivate their own account")
    void anAdministratorCannotLockThemselvesOut() throws Exception {
        mockMvc.perform(deactivate(root, root))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));

        assertThat(users.findById(root).orElseThrow().isActive())
                .as("the one account that could undo the mistake must survive it")
                .isTrue();
    }

    @Test
    @DisplayName("a missing 'active' field is refused rather than read as a deactivation")
    void anOmittedActiveFieldIsRejected() throws Exception {
        mockMvc.perform(authed(patch("/api/admin/users/" + leaver + "/active"), root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(users.findById(leaver).orElseThrow().isActive()).isTrue();
    }

    // ─── §5 #13 — the floor plan changes ─────────────────────────────────────────

    @Test
    @DisplayName("withdrawing a desk releases the bookings on it and names who lost one")
    void withdrawingADeskReleasesItsBookings() throws Exception {
        Booking erins = book(erin, "R4-A1", TODAY);
        Booking leavers = book(leaver, "R4-A1", TODAY.plusDays(3));

        mockMvc.perform(setSeatStatus(root, seatId("R4-A1"), SeatStatus.BROKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.status").value("BROKEN"))
                .andExpect(jsonPath("$.bookingsReleased").value(2))
                .andExpect(jsonPath("$.released[0].userDisplayName").value("Erin Employee"));

        assertThat(statusOf(erins)).isEqualTo(BookingStatus.RELEASED_SEAT_REMOVED);
        assertThat(statusOf(leavers)).isEqualTo(BookingStatus.RELEASED_SEAT_REMOVED);
    }

    @Test
    @DisplayName("a withdrawn desk is soft-deleted, never removed — the row and its history survive")
    void aWithdrawnDeskIsSoftDeleted() throws Exception {
        Booking erins = book(erin, "R4-A1", TODAY);
        long seatId = seatId("R4-A1");

        mockMvc.perform(setSeatStatus(root, seatId, SeatStatus.DISABLED)).andExpect(status().isOk());

        assertThat(seats.findById(seatId))
                .as("booking.seat_id is ON DELETE RESTRICT precisely so a refit cannot erase history")
                .isPresent();
        assertThat(bookings.findById(erins.getId())).isPresent();
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.DISABLED);
    }

    @Test
    @DisplayName("a withdrawn desk cannot be claimed, and can be put back")
    void aWithdrawnDeskCannotBeClaimedUntilItIsRestored() throws Exception {
        long seatId = seatId("R4-A1");
        mockMvc.perform(setSeatStatus(root, seatId, SeatStatus.BROKEN)).andExpect(status().isOk());

        assertThatThrownBy(() -> bookingService.claim(erin, seatId, TODAY, null))
                .isInstanceOf(SeatNotBookableException.class);

        mockMvc.perform(setSeatStatus(root, seatId, SeatStatus.ACTIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsReleased").value(0));

        assertThat(bookingService.claim(erin, seatId, TODAY, null).seatLabel()).isEqualTo("R4-A1");
    }

    @Test
    @DisplayName("withdrawing a desk leaves yesterday's booking on it alone")
    void withdrawingADeskLeavesHistoryAlone() throws Exception {
        Booking lastWeek = book(erin, "R4-A1", TODAY.minusDays(4));

        mockMvc.perform(setSeatStatus(root, seatId("R4-A1"), SeatStatus.DISABLED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsReleased").value(0));

        assertThat(statusOf(lastWeek)).isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    @DisplayName("setting a desk to the status it already has changes nothing")
    void settingTheSameSeatStatusIsANoOp() throws Exception {
        Booking erins = book(erin, "R4-A1", TODAY);

        mockMvc.perform(setSeatStatus(root, seatId("R4-A1"), SeatStatus.ACTIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasAlreadyInThatState").value(true))
                .andExpect(jsonPath("$.bookingsReleased").value(0));

        assertThat(statusOf(erins)).isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    @DisplayName("an unknown user or seat is a 404, not a silent success")
    void unknownTargetsAre404() throws Exception {
        mockMvc.perform(deactivate(root, 987_654L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_USER_NOT_FOUND"));

        mockMvc.perform(setSeatStatus(root, 987_654L, SeatStatus.BROKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_SEAT_NOT_FOUND"));
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder deactivate(long actingUserId, long targetUserId) throws Exception {
        return setActive(actingUserId, targetUserId, false);
    }

    private MockHttpServletRequestBuilder setActive(long actingUserId, long targetUserId, boolean active)
            throws Exception {
        return authed(patch("/api/admin/users/" + targetUserId + "/active"), actingUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("active", active)));
    }

    private MockHttpServletRequestBuilder setSeatStatus(long actingUserId, long seatId, SeatStatus status)
            throws Exception {
        return authed(patch("/api/admin/seats/" + seatId + "/status"), actingUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status.name())));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, long userId) {
        try {
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(userId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String tokenFor(long userId) throws Exception {
        AppUser owner = users.findById(userId).orElseThrow();
        JsonNode login = body(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", owner.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()));
        return login.path("accessToken").asText();
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private long person(String email, String displayName, UserRole role) {
        AppUser user = new AppUser(email, displayName, role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.saveAndFlush(user).getId();
    }

    /**
     * Saved straight through the repository rather than via {@code BookingService}, so a fixture can
     * put a booking on a past date — which the claim rules correctly refuse — without the test
     * having to move the clock backwards to arrange it.
     */
    private Booking book(long userId, String seatLabel, LocalDate date) {
        return bookings.saveAndFlush(new Booking(seat(seatLabel),
                users.findById(userId).orElseThrow(), date, null));
    }

    private BookingStatus statusOf(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow().getStatus();
    }

    private Seat seat(String label) {
        return seats.findByLabel(label).orElseThrow();
    }

    private long seatId(String label) {
        return seat(label).getId();
    }

    private void clearEverything() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    private void restoreEverySeat() {
        List<Seat> changed = seats.findAll().stream()
                .filter(seat -> seat.getStatus() != SeatStatus.ACTIVE)
                .peek(seat -> seat.setStatus(SeatStatus.ACTIVE))
                .toList();
        if (!changed.isEmpty()) {
            seats.saveAllAndFlush(changed);
        }
    }
}
