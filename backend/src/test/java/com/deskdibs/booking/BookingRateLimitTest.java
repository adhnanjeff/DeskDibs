package com.deskdibs.booking;

import com.deskdibs.auth.AbstractAuthWebTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-user throttling of booking mutations (PLAN.md §5 #14).
 *
 * <p>Deliberately switched on for this class alone — the suite runs with it off so the 150-thread
 * concurrency test measures the database constraint rather than the limiter. A tiny budget of three
 * operations a minute keeps the test fast and makes the boundary obvious.
 */
@Import(ControllableClockConfiguration.class)
@TestPropertySource(properties = {
        "deskdibs.rate-limit.enabled=true",
        "deskdibs.rate-limit.booking-ops-per-minute=3",
})
class BookingRateLimitTest extends AbstractAuthWebTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final String PASSWORD = "correct horse battery staple";

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final PasswordEncoder passwordEncoder;

    private long alice;
    private long bob;

    BookingRateLimitTest(MockMvc mockMvc,
                         ObjectMapper json,
                         AppUserRepository users,
                         BookingRepository bookings,
                         SeatRepository seats,
                         PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.users = users;
        this.bookings = bookings;
        this.seats = seats;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
        alice = person("alice@deskdibs.test", "Alice M.", UserRole.EMPLOYEE);
        bob = person("bob@deskdibs.test", "Bob T.", UserRole.EMPLOYEE);
    }

    @Test
    @DisplayName("a burst of booking calls from one account is refused with 429 once the budget runs out")
    void refusesTheFourthBookingCallInAMinute() throws Exception {
        String token = tokenFor(alice);

        // Three land — the whole minute's budget. They fail on their own merits (only the first
        // claim can succeed; the rest are 409s for holding a seat that day already), which is
        // exactly the point: the limiter counts attempts, not successes, or a script could spin
        // on failures for free.
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(claim(token, seatId("R6-A" + i)))
                    .andExpect(status().is(not429()));
        }

        mockMvc.perform(claim(token, seatId("R6-B1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Try again")));
    }

    @Test
    @DisplayName("one user's burst does not throttle anybody else")
    void bucketsAreHeldPerUserNotGlobally() throws Exception {
        String alices = tokenFor(alice);
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(claim(alices, seatId("R6-A" + Math.min(i, 3))));
        }

        // Alice is out of budget; Bob has not spent any of his.
        mockMvc.perform(claim(alices, seatId("R7-A1")))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(claim(tokenFor(bob), seatId("R7-A1")))
                .andExpect(status().is(not429()));

        assertThat(bookings.countBySeatIdAndBookingDateAndStatus(seatId("R7-A1"), TODAY, BookingStatus.ACTIVE))
                .as("Bob's claim went through rather than being refused as somebody else's traffic")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reading the seat map is never throttled, however often a dashboard polls it")
    void readsAreNotThrottled() throws Exception {
        String token = tokenFor(alice);
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(authed(get("/api/seatmap"), token))
                    .andExpect(status().isOk());
        }
        // The budget was never touched, so a mutation still works afterwards.
        mockMvc.perform(claim(token, seatId("R6-A1")))
                .andExpect(status().is(not429()));
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    /** Any outcome except a throttle: the limiter's job is orthogonal to whether a claim wins. */
    private static org.hamcrest.Matcher<Integer> not429() {
        return org.hamcrest.Matchers.not(429);
    }

    private MockHttpServletRequestBuilder claim(String token, long seatId) throws Exception {
        return authed(post("/api/bookings"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("seatId", seatId, "date", TODAY.toString())));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String tokenFor(long userId) throws Exception {
        AppUser owner = users.findById(userId).orElseThrow();
        JsonNode login = json.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", owner.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return login.path("accessToken").asText();
    }

    private long person(String email, String displayName, UserRole role) {
        AppUser user = new AppUser(email, displayName, role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.saveAndFlush(user).getId();
    }

    private long seatId(String label) {
        Seat seat = seats.findByLabel(label).orElseThrow();
        return seat.getId();
    }
}
