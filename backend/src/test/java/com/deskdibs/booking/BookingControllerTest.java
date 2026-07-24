package com.deskdibs.booking;

import com.deskdibs.auth.AbstractAuthWebTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.seat.Seat;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The booking endpoints through the real HTTP stack, one layer above {@link BookingServiceClaimTest}
 * and {@link BookingServiceLifecycleTest}. Those prove the rules; this proves the wiring on top of
 * them — that {@code AuthExceptionHandler} turns each domain exception into the right status and
 * body, that {@code CurrentUser} resolves the real caller rather than a parameter a test supplied
 * directly, and that a client sees exactly the structured fields PLAN.md promises: who won a
 * contested seat, which seat a caller already holds, which team is blocking them.
 */
@Import(ControllableClockConfiguration.class)
class BookingControllerTest extends AbstractAuthWebTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final String PASSWORD = "correct horse battery staple";

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final SeatReservationRepository reservations;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final PasswordEncoder passwordEncoder;

    private long alice;
    private long bob;
    private long dana;

    BookingControllerTest(MockMvc mockMvc,
                          ObjectMapper json,
                          AppUserRepository users,
                          BookingRepository bookings,
                          SeatRepository seats,
                          SeatReservationRepository reservations,
                          TeamRepository teams,
                          TeamMemberRepository teamMembers,
                          PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.users = users;
        this.bookings = bookings;
        this.seats = seats;
        this.reservations = reservations;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() throws Exception {
        clearEverything();
        restoreEverySeat();

        alice = person("alice@deskdibs.test", "Alice M.");
        bob = person("bob@deskdibs.test", "Bob T.");
        dana = person("dana@deskdibs.test", "Dana K.");
    }

    @AfterEach
    void leaveNobodyBehind() {
        clearEverything();
        restoreEverySeat();
    }

    @Test
    @DisplayName("claiming a free seat returns 201 with the booking")
    void claimingAFreeSeatReturns201() throws Exception {
        mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatLabel").value("R3-A1"))
                .andExpect(jsonPath("$.userId").value(alice))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("a second claim on the same seat returns 409 naming the winner")
    void aSecondClaimOnTheSameSeatNamesTheWinner() throws Exception {
        mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, null)).andExpect(status().isCreated());

        mockMvc.perform(claimRequest(bob, "R3-A1", TODAY, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.details.seatLabel").value("R3-A1"))
                .andExpect(jsonPath("$.details.takenByUserId").value(alice))
                .andExpect(jsonPath("$.details.takenByDisplayName").value("Alice M."));
    }

    @Test
    @DisplayName("claiming a second seat on a day you already hold one names the seat you hold")
    void claimingASecondSeatNamesTheOneYouAlreadyHold() throws Exception {
        mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, null)).andExpect(status().isCreated());

        mockMvc.perform(claimRequest(alice, "R3-A2", TODAY, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BOOKED_THAT_DAY"))
                .andExpect(jsonPath("$.details.existingSeatLabel").value("R3-A1"));
    }

    @Test
    @DisplayName("replaying the same Idempotency-Key returns the original booking, not a 409")
    void replayingAnIdempotencyKeyReturnsTheOriginal() throws Exception {
        String key = UUID.randomUUID().toString();

        JsonNode first = body(mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, key))
                .andExpect(status().isCreated()));

        mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first.path("id").asLong()));

        assertThat(bookings.count()).as("no second row for a replayed key").isEqualTo(1);
    }

    @Test
    @DisplayName("an out-of-window date returns 400 with the allowed range")
    void anOutOfWindowDateReturns400WithTheAllowedRange() throws Exception {
        mockMvc.perform(claimRequest(alice, "R3-A1", TODAY.minusDays(1), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_OUTSIDE_BOOKING_WINDOW"))
                .andExpect(jsonPath("$.details.earliestAllowed").value(TODAY.toString()));
    }

    @Test
    @DisplayName("a non-team member hitting a held seat gets 403 naming the team")
    void aNonTeamMemberHittingAHeldSeatGets403() throws Exception {
        Team platform = teams.saveAndFlush(new Team("Platform", user(dana)));
        teamMembers.saveAndFlush(new TeamMember(platform, user(dana)));
        reservations.saveAndFlush(new SeatReservation(seat("R3-A1"), platform, TODAY, TODAY, user(dana)));

        mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEAT_RESERVED_FOR_TEAM"))
                .andExpect(jsonPath("$.details.teamName").value("Platform"));
    }

    @Test
    @DisplayName("cancelling somebody else's booking is refused; the owner may cancel their own")
    void cancelIsOwnerOnlyThroughHttp() throws Exception {
        JsonNode booking = body(mockMvc.perform(claimRequest(alice, "R3-A1", TODAY, null))
                .andExpect(status().isCreated()));
        long bookingId = booking.path("id").asLong();

        mockMvc.perform(authed(delete("/api/bookings/" + bookingId), bob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BOOKING_ACCESS_DENIED"));

        mockMvc.perform(authed(delete("/api/bookings/" + bookingId), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("no endpoint leaks a stack trace or an exception class name on error")
    void noEndpointLeaksAStackTrace() throws Exception {
        String responseBody = mockMvc.perform(claimRequest(alice, "R3-A1", TODAY.minusDays(1), null))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody)
                .doesNotContain("Exception")
                .doesNotContain("at com.deskdibs")
                .doesNotContain("java.lang");
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder claimRequest(long userId, String seatLabel, LocalDate date,
                                                       String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = authed(post("/api/bookings"), userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("seatId", seat(seatLabel).getId(), "date", date.toString())));
        return idempotencyKey == null ? request : request.header("Idempotency-Key", idempotencyKey);
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

    private JsonNode body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private long person(String email, String displayName) {
        AppUser user = new AppUser(email, displayName, UserRole.EMPLOYEE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.saveAndFlush(user).getId();
    }

    private AppUser user(long id) {
        return users.findById(id).orElseThrow();
    }

    private Seat seat(String label) {
        return seats.findByLabel(label).orElseThrow();
    }

    private void clearEverything() {
        bookings.deleteAllInBatch();
        reservations.deleteAllInBatch();
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
