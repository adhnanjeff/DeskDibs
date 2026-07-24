package com.deskdibs.booking;

import com.deskdibs.auth.AbstractAuthWebTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatMapState;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservation;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.seat.SeatStatus;
import com.deskdibs.team.Team;
import com.deskdibs.team.TeamMember;
import com.deskdibs.team.TeamMemberRepository;
import com.deskdibs.team.TeamRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/seatmap} through the real HTTP stack: the endpoint the whole product hangs off.
 *
 * <p>Two things matter beyond "does it return the right JSON". First, whether it stays one round
 * trip as booking count grows — {@link SeatMapService}'s javadoc promises three bounded queries
 * regardless of how many of the 110 seats are occupied, and the only way to hold that promise
 * honestly is to count the actual queries Hibernate issues, not just eyeball the code. Second,
 * whether the state a seat renders as matches what the booking and reservation tables actually
 * say, including the occupant's name, which is the one piece of data every other test in this class
 * treats as the reason the endpoint exists.
 */
@Import(ControllableClockConfiguration.class)
class SeatMapControllerTest extends AbstractAuthWebTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final SeatReservationRepository reservations;
    private final PasswordEncoder passwordEncoder;
    private final EntityManagerFactory entityManagerFactory;
    private final OfficeProperties office;

    private String token;
    private long employeeId;

    SeatMapControllerTest(MockMvc mockMvc,
                          ObjectMapper json,
                          AppUserRepository users,
                          BookingRepository bookings,
                          SeatRepository seats,
                          TeamRepository teams,
                          TeamMemberRepository teamMembers,
                          SeatReservationRepository reservations,
                          PasswordEncoder passwordEncoder,
                          EntityManagerFactory entityManagerFactory,
                          OfficeProperties office) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.users = users;
        this.bookings = bookings;
        this.seats = seats;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.reservations = reservations;
        this.passwordEncoder = passwordEncoder;
        this.entityManagerFactory = entityManagerFactory;
        this.office = office;
    }

    @BeforeEach
    void resetTheOfficeAndSignIn() throws Exception {
        clearBookingsTeamsAndPeople();
        restoreEverySeat();

        employeeId = person("erin@deskdibs.test", "Erin Employee", UserRole.EMPLOYEE);
        token = loginAndGetToken("erin@deskdibs.test");
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearBookingsTeamsAndPeople();
        restoreEverySeat();
    }

    @Test
    @DisplayName("the map covers all 110 seats and names the occupant of a taken one")
    void theMapCoversAllSeatsAndNamesTheOccupant() throws Exception {
        long bob = person("bob@deskdibs.test", "Bob T.", UserRole.EMPLOYEE);
        bookings.saveAndFlush(new Booking(seat("R1-A1"), user(bob), TODAY, null));

        JsonNode response = json.readTree(mockMvc.perform(authed(get("/api/seatmap").param("date", TODAY.toString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(response.path("date").asText()).isEqualTo(TODAY.toString());

        List<JsonNode> allSeats = new ArrayList<>();
        response.path("floors").forEach(floor -> floor.path("zones").forEach(
                zone -> zone.path("tables").forEach(table -> table.path("seats").forEach(allSeats::add))));

        assertThat(allSeats).as("every seeded seat is in the map").hasSize(110);
        assertThat(allSeats.stream().map(s -> s.path("seatLabel").asText()).distinct().count())
                .as("no seat appears twice")
                .isEqualTo(110);

        JsonNode bobsSeat = allSeats.stream().filter(s -> s.path("seatLabel").asText().equals("R1-A1"))
                .findFirst().orElseThrow();
        assertThat(bobsSeat.path("state").asText()).isEqualTo(SeatMapState.OCCUPIED.name());
        assertThat(bobsSeat.path("occupantUserId").asLong()).isEqualTo(bob);
        assertThat(bobsSeat.path("occupantDisplayName").asText()).isEqualTo("Bob T.");
        assertThat(bobsSeat.path("checkedIn").asBoolean()).isFalse();

        JsonNode freeSeat = allSeats.stream().filter(s -> s.path("seatLabel").asText().equals("R1-A2"))
                .findFirst().orElseThrow();
        assertThat(freeSeat.path("state").asText()).isEqualTo(SeatMapState.AVAILABLE.name());
        assertThat(freeSeat.path("occupantDisplayName").isNull()).isTrue();
    }

    @Test
    @DisplayName("defaults to today, in the office timezone, when no date is given")
    void defaultsToTodayWhenDateOmitted() throws Exception {
        mockMvc.perform(authed(get("/api/seatmap")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.date").value(TODAY.toString()));
    }

    @Test
    @DisplayName("a seat reserved for a team the caller is not on shows as team-reserved, not available")
    void aTeamHoldShowsAsTeamReservedForAnOutsider() throws Exception {
        long dana = person("dana@deskdibs.test", "Dana K.", UserRole.MANAGER);
        Team platform = teams.saveAndFlush(new Team("Platform", user(dana)));
        reservations.saveAndFlush(new SeatReservation(seat("R2-A1"), platform, TODAY, TODAY, user(dana)));

        JsonNode response = json.readTree(mockMvc.perform(authed(get("/api/seatmap").param("date", TODAY.toString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode heldSeat = findSeat(response, "R2-A1");
        assertThat(heldSeat.path("state").asText()).isEqualTo(SeatMapState.TEAM_RESERVED.name());
        assertThat(heldSeat.path("reservedForTeamName").asText()).isEqualTo("Platform");
    }

    /**
     * The claim in {@link SeatMapService}'s javadoc, held to account: three queries regardless of
     * how many of the 110 seats have a booking. Zero bookings and twenty bookings must cost the
     * database the same number of round trips, or the "one HTTP round trip, not one per seat"
     * promise is just a comment.
     */
    @Test
    @DisplayName("the seat map costs the same number of queries whether nothing or twenty seats are booked")
    void theMapIsOneRoundTripRegardlessOfHowManySeatsAreBooked() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        statistics.clear();
        mockMvc.perform(authed(get("/api/seatmap").param("date", TODAY.toString()))).andExpect(status().isOk());
        long queriesWithNoBookings = statistics.getQueryExecutionCount();

        List<Seat> allSeats = seats.findAll();
        for (int i = 0; i < 20; i++) {
            long person = person("person" + i + "@deskdibs.test", "Person " + i, UserRole.EMPLOYEE);
            bookings.saveAndFlush(new Booking(allSeats.get(i), user(person), TODAY, null));
        }

        statistics.clear();
        mockMvc.perform(authed(get("/api/seatmap").param("date", TODAY.toString()))).andExpect(status().isOk());
        long queriesWithTwentyBookings = statistics.getQueryExecutionCount();

        assertThat(queriesWithTwentyBookings)
                .as("occupying 20 more seats must not cost 20 more queries")
                .isEqualTo(queriesWithNoBookings);
        assertThat(queriesWithTwentyBookings)
                .as("a small, bounded number of queries — not one per seat, and not one per booking")
                .isLessThanOrEqualTo(5);
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode findSeat(JsonNode response, String label) {
        List<JsonNode> match = new ArrayList<>();
        response.path("floors").forEach(floor -> floor.path("zones").forEach(
                zone -> zone.path("tables").forEach(table -> table.path("seats").forEach(s -> {
                    if (s.path("seatLabel").asText().equals(label)) {
                        match.add(s);
                    }
                }))));
        return match.stream().findFirst().orElseThrow();
    }

    private String loginAndGetToken(String email) throws Exception {
        JsonNode login = json.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return login.path("accessToken").asText();
    }

    private static final String TEST_PASSWORD = "correct horse battery staple";

    private long person(String email, String displayName, UserRole role) {
        AppUser user = new AppUser(email, displayName, role);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        return users.saveAndFlush(user).getId();
    }

    private AppUser user(long id) {
        return users.findById(id).orElseThrow();
    }

    private Seat seat(String label) {
        return seats.findByLabel(label).orElseThrow();
    }

    private void clearBookingsTeamsAndPeople() {
        bookings.deleteAllInBatch();
        teamMembers.deleteAllInBatch();
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
