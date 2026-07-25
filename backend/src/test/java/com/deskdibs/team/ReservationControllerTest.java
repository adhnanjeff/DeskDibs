package com.deskdibs.team;

import com.deskdibs.auth.AbstractAuthWebTest;
import com.deskdibs.booking.Booking;
import com.deskdibs.booking.BookingRepository;
import com.deskdibs.booking.BookingStatus;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservationRepository;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/reservations} through the real HTTP stack: role gating enforced by
 * {@code @PreAuthorize} against the database-sourced role, and the partial-success contract
 * PLAN.md §4/§7 promises — a manager reserving seats that include one already booked gets a report
 * naming the conflict, and the existing booking is never touched.
 */
@Import(ControllableClockConfiguration.class)
class ReservationControllerTest extends AbstractAuthWebTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final String PASSWORD = "correct horse battery staple";

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final SeatReservationRepository reservations;
    private final TeamRepository teams;
    private final PasswordEncoder passwordEncoder;
    private final MutableClock clock;
    private final ZoneId office;

    private long dana;
    private long erin;
    private long bob;
    private long platformTeamId;

    ReservationControllerTest(MockMvc mockMvc,
                              ObjectMapper json,
                              AppUserRepository users,
                              BookingRepository bookings,
                              SeatRepository seats,
                              SeatReservationRepository reservations,
                              TeamRepository teams,
                              PasswordEncoder passwordEncoder,
                              MutableClock clock,
                              OfficeProperties officeProperties) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.users = users;
        this.bookings = bookings;
        this.seats = seats;
        this.reservations = reservations;
        this.teams = teams;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.office = officeProperties.timezone();
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        // The clock bean is cached with the Spring context, so a test that moves it past a cut-off
        // would otherwise leak that time into every test after it.
        clock.setTo(ZonedDateTime.of(TODAY, ControllableClockConfiguration.DEFAULT_TIME_OF_DAY, office));
        clearEverything();
        restoreEverySeat();

        dana = person("dana@deskdibs.test", "Dana K.", UserRole.MANAGER);
        erin = person("erin@deskdibs.test", "Erin Employee", UserRole.EMPLOYEE);
        bob = person("bob@deskdibs.test", "Bob T.", UserRole.EMPLOYEE);
        platformTeamId = teams.saveAndFlush(new Team("Platform", user(dana))).getId();
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearEverything();
        restoreEverySeat();
    }

    @Test
    @DisplayName("an employee cannot hold seats for a team")
    void anEmployeeCannotCreateAReservation() throws Exception {
        mockMvc.perform(reservationRequest(erin, platformTeamId, List.of(seatId("R4-A1")), TODAY, TODAY))
                .andExpect(status().isForbidden());

        assertThat(reservations.count()).isZero();
    }

    @Test
    @DisplayName("a manager can hold seats for a team")
    void aManagerCanCreateAReservation() throws Exception {
        mockMvc.perform(reservationRequest(dana, platformTeamId,
                        List.of(seatId("R4-A1"), seatId("R4-A2")), TODAY, TODAY.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("Platform"))
                .andExpect(jsonPath("$.held.length()").value(2))
                .andExpect(jsonPath("$.unavailable.length()").value(0));

        assertThat(reservations.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("reserving over an already-booked seat reports it unavailable and cancels nobody")
    void reservingOverABookedSeatReportsItAndCancelsNobody() throws Exception {
        Booking bobsBooking = bookings.saveAndFlush(new Booking(seat("R4-A1"), user(bob), TODAY, null));

        mockMvc.perform(reservationRequest(dana, platformTeamId,
                        List.of(seatId("R4-A1"), seatId("R4-A2")), TODAY, TODAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.held.length()").value(1))
                .andExpect(jsonPath("$.held[0].seatLabel").value("R4-A2"))
                .andExpect(jsonPath("$.unavailable.length()").value(1))
                .andExpect(jsonPath("$.unavailable[0].seatLabel").value("R4-A1"))
                .andExpect(jsonPath("$.unavailable[0].conflictingUserDisplayName").value("Bob T."));

        assertThat(bookings.findById(bobsBooking.getId()).orElseThrow().getStatus())
                .as("the system never force-cancels a booking to make room for a hold")
                .isEqualTo(BookingStatus.ACTIVE);
        assertThat(reservations.count()).as("only the free seat was held").isEqualTo(1);
    }

    @Test
    @DisplayName("the reservation's creator may release it; an unrelated employee may not")
    void releaseIsRestrictedToTheCreatorManagerOrAdmin() throws Exception {
        JsonNode report = body(mockMvc.perform(reservationRequest(dana, platformTeamId,
                        List.of(seatId("R4-A1")), TODAY, TODAY))
                .andExpect(status().isOk()));
        long reservationId = report.path("held").get(0).path("reservationId").asLong();

        mockMvc.perform(authed(delete("/api/reservations/" + reservationId), erin))
                .andExpect(status().isForbidden());
        assertThat(reservations.existsById(reservationId)).isTrue();

        mockMvc.perform(authed(delete("/api/reservations/" + reservationId), dana))
                .andExpect(status().isNoContent());
        assertThat(reservations.existsById(reservationId)).isFalse();
    }

    @Test
    @DisplayName("a manager cannot hold desks in another team's name")
    void aManagerCannotReserveForATeamTheyDoNotManage() throws Exception {
        long otherManager = person("mo@deskdibs.test", "Mo R.", UserRole.MANAGER);
        long designTeam = teams.saveAndFlush(new Team("Design", user(otherManager))).getId();

        // Dana manages Platform, not Design. Holding a block of desks under somebody else's team
        // is a mutation on their team, so the MANAGER role alone must not be enough.
        mockMvc.perform(reservationRequest(dana, designTeam, List.of(seatId("R4-A1")), TODAY, TODAY))
                .andExpect(status().isForbidden());

        assertThat(reservations.count()).isZero();
    }

    @Test
    @DisplayName("an admin may hold seats for any team")
    void anAdminMayReserveForAnyTeam() throws Exception {
        long root = person("root@deskdibs.test", "Root A.", UserRole.ADMIN);

        mockMvc.perform(reservationRequest(root, platformTeamId, List.of(seatId("R4-A1")), TODAY, TODAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.held.length()").value(1));
    }

    @Test
    @DisplayName("a manager is offered only the teams they manage; an admin sees them all")
    void teamsEndpointOffersOnlyWhatCreateWouldAccept() throws Exception {
        long otherManager = person("mo@deskdibs.test", "Mo R.", UserRole.MANAGER);
        teams.saveAndFlush(new Team("Design", user(otherManager)));
        long root = person("root@deskdibs.test", "Root A.", UserRole.ADMIN);

        mockMvc.perform(authed(get("/api/reservations/teams"), dana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Platform"));

        mockMvc.perform(authed(get("/api/reservations/teams"), root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(authed(get("/api/reservations/teams"), erin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listing holds returns only the ones the caller could actually release")
    void listReturnsOnlyHoldsTheCallerMayAct0n() throws Exception {
        long otherManager = person("mo@deskdibs.test", "Mo R.", UserRole.MANAGER);
        long designTeam = teams.saveAndFlush(new Team("Design", user(otherManager))).getId();

        mockMvc.perform(reservationRequest(dana, platformTeamId, List.of(seatId("R4-A1")), TODAY, TODAY))
                .andExpect(status().isOk());
        mockMvc.perform(reservationRequest(otherManager, designTeam, List.of(seatId("R4-A2")), TODAY, TODAY))
                .andExpect(status().isOk());

        // Every row Dana gets back is one the delete endpoint would accept from her.
        mockMvc.perform(authed(get("/api/reservations"), dana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teamName").value("Platform"))
                .andExpect(jsonPath("$[0].seatLabel").value("R4-A1"));
    }

    @Test
    @DisplayName("a hold for today reports itself released once the morning cut-off has passed")
    void aHoldForTodayStopsBeingEnforcedAfterItsReleaseTime() throws Exception {
        mockMvc.perform(reservationRequest(dana, platformTeamId, List.of(seatId("R4-A1")), TODAY, TODAY))
                .andExpect(status().isOk());

        // 09:00 — before the 10:00 team-block release, so the block is holding its desk.
        mockMvc.perform(authed(get("/api/reservations"), dana))
                .andExpect(jsonPath("$[0].enforcedNow").value(true));

        clock.setTo(ZonedDateTime.of(TODAY, LocalTime.of(10, 1), office));

        // One minute later the hold still exists but holds nothing — the soft release, with no job
        // and no state change. The UI needs to say so, or the manager sees a hold here and no hold
        // on the map and concludes the feature is broken.
        mockMvc.perform(authed(get("/api/reservations"), dana))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].enforcedNow").value(false));
    }

    @Test
    @DisplayName("a hold that has already ended is not listed as something to release")
    void listOmitsHoldsThatHaveAlreadyEnded() throws Exception {
        mockMvc.perform(reservationRequest(dana, platformTeamId,
                        List.of(seatId("R4-A1")), TODAY.minusDays(5), TODAY.minusDays(3)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/reservations"), dana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder reservationRequest(long actingUserId, long teamId, List<Long> seatIds,
                                                             LocalDate startDate, LocalDate endDate)
            throws Exception {
        return authed(post("/api/reservations"), actingUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "teamId", teamId,
                        "seatIds", seatIds,
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString())));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, long userId) {
        try {
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(userId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String tokenFor(long userId) throws Exception {
        AppUser owner = user(userId);
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

    private AppUser user(long id) {
        return users.findById(id).orElseThrow();
    }

    private Seat seat(String label) {
        return seats.findByLabel(label).orElseThrow();
    }

    private long seatId(String label) {
        return seat(label).getId();
    }

    private void clearEverything() {
        bookings.deleteAllInBatch();
        reservations.deleteAllInBatch();
        teams.deleteAllInBatch();
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
