package com.deskdibs.realtime;

import com.deskdibs.booking.Booking;
import com.deskdibs.booking.BookingRepository;
import com.deskdibs.booking.SeatAvailabilityChangedEvent;
import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatMapView;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The WebSocket half of Phase 4, proved two different ways.
 *
 * <p>{@link #anEventPublishedInACommittedTransactionReachesTheListener()} and
 * {@link #anEventPublishedInARolledBackTransactionNeverReachesTheListener()} isolate the one
 * correctness rule that matters here — {@code @TransactionalEventListener(AFTER_COMMIT)} must never
 * fire for a transaction that rolls back — from {@code BookingService}'s specific call sites, by
 * publishing {@link SeatAvailabilityChangedEvent} through a transaction this test controls directly.
 * A committed transaction is expected to deliver the broadcast synchronously, before
 * {@code TransactionTemplate.executeWithoutResult} even returns, since {@code AFTER_COMMIT}
 * synchronizations run as part of the commit itself for a resource-local transaction manager — so
 * this half of the test needs no polling or timeout to be deterministic.
 *
 * <p>The remaining tests open a real STOMP connection over a real embedded server to prove the
 * wiring end to end: that an unauthenticated socket is refused before it can subscribe to anything,
 * and that a genuine HTTP claim is what a subscribed client actually sees arrive.
 */
@Import(ControllableClockConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeBroadcastTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final String PASSWORD = "correct horse battery staple";

    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final int port;

    private long contestedSeatId;

    RealtimeBroadcastTest(AppUserRepository users,
                          BookingRepository bookings,
                          SeatRepository seats,
                          PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher events,
                          TransactionTemplate transactionTemplate,
                          @LocalServerPort int port) {
        this.users = users;
        this.bookings = bookings;
        this.seats = seats;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.port = port;
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
        restoreEverySeat();
        contestedSeatId = seat("R5-A1").getId();
    }

    @AfterEach
    void leaveNobodyBehind() {
        bookings.deleteAllInBatch();
        users.deleteAllInBatch();
        restoreEverySeat();
    }

    // ─── The correctness rule: AFTER_COMMIT, never mid-transaction ───────────────

    @Test
    @DisplayName("an event published in a transaction that rolls back never reaches the listener")
    void anEventPublishedInARolledBackTransactionNeverReachesTheListener() throws Exception {
        BlockingQueue<SeatStatusChanged> received = new LinkedBlockingQueue<>();
        long userId = person("watcher@deskdibs.test", "Watcher");
        StompSession session = connectAndSubscribe(userId, TODAY, received);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                events.publishEvent(new SeatAvailabilityChangedEvent(contestedSeatId, TODAY));
                status.setRollbackOnly();
            });

            SeatStatusChanged message = received.poll(400, TimeUnit.MILLISECONDS);
            assertThat(message)
                    .as("broadcasting inside a transaction that then rolls back would tell every open "
                            + "map a seat changed and leave it wrong until refresh — the listener must "
                            + "not fire at all")
                    .isNull();
        } finally {
            session.disconnect();
        }
    }

    @Test
    @DisplayName("an event published in a transaction that commits reaches the listener")
    void anEventPublishedInACommittedTransactionReachesTheListener() throws Exception {
        BlockingQueue<SeatStatusChanged> received = new LinkedBlockingQueue<>();
        long userId = person("watcher2@deskdibs.test", "Watcher Two");
        StompSession session = connectAndSubscribe(userId, TODAY, received);

        try {
            transactionTemplate.executeWithoutResult(status ->
                    events.publishEvent(new SeatAvailabilityChangedEvent(contestedSeatId, TODAY)));

            SeatStatusChanged message = received.poll(2, TimeUnit.SECONDS);
            assertThat(message).as("a committed transaction's event must reach the topic").isNotNull();
            assertThat(message.seat().seatId()).isEqualTo(contestedSeatId);
            assertThat(message.bookingDate()).isEqualTo(TODAY);
        } finally {
            session.disconnect();
        }
    }

    // ─── Real STOMP wiring ────────────────────────────────────────────────────────

    @Test
    @DisplayName("connecting without a bearer token is refused before any subscription is possible")
    void connectingWithoutATokenIsRefused() {
        WebSocketStompClient client = stompClient();
        StompHeaders connectHeaders = new StompHeaders();
        // Deliberately no Authorization header — the exact frame StompAuthChannelInterceptor rejects.

        assertThatThrownBy(() -> client.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                        noOpHandler())
                .get(3, TimeUnit.SECONDS))
                .as("a CONNECT frame with no bearer token must never yield an open, usable session")
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    @DisplayName("a genuine HTTP claim is what a subscribed, authenticated client actually sees arrive")
    void aRealClaimArrivesOnTheTopicOverTheWire() throws Exception {
        long claimant = person("frank@deskdibs.test", "Frank L.");
        BlockingQueue<SeatStatusChanged> received = new LinkedBlockingQueue<>();
        StompSession session = connectAndSubscribe(claimant, TODAY, received);

        try {
            String token = tokenFor(claimant);
            RestClient.create().post()
                    .uri("http://localhost:" + port + "/api/bookings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("seatId", contestedSeatId, "date", TODAY.toString()))
                    .retrieve()
                    .toBodilessEntity();

            SeatStatusChanged message = received.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("the claim over HTTP must produce a broadcast the socket receives").isNotNull();
            assertThat(message.seat().seatId()).isEqualTo(contestedSeatId);
            assertThat(message.seat().occupantDisplayName()).isEqualTo("Frank L.");
        } finally {
            session.disconnect();
        }
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private StompSession connectAndSubscribe(long userId, LocalDate date, BlockingQueue<SeatStatusChanged> sink)
            throws Exception {
        WebSocketStompClient client = stompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(userId));

        StompSession session = client.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                        noOpHandler())
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/seatmap/" + date, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SeatStatusChanged.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((SeatStatusChanged) payload);
            }
        });
        return session;
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.getObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        client.setMessageConverter(converter);
        return client;
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private StompSessionHandlerNoOp noOpHandler() {
        return new StompSessionHandlerNoOp();
    }

    private String tokenFor(long userId) {
        AppUser owner = users.findById(userId).orElseThrow();
        ObjectMapper json = new ObjectMapper();
        try {
            String body = RestClient.create().post()
                    .uri("http://localhost:" + port + "/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", owner.getEmail(), "password", PASSWORD))
                    .retrieve()
                    .body(String.class);
            JsonNode login = json.readTree(body);
            return login.path("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException("could not obtain a token for user " + userId, e);
        }
    }

    private long person(String email, String displayName) {
        AppUser user = new AppUser(email, displayName, UserRole.EMPLOYEE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.saveAndFlush(user).getId();
    }

    private Seat seat(String label) {
        return seats.findByLabel(label).orElseThrow();
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

    /** Only {@code connectAsync}'s handshake result matters to these tests, never session-level events. */
    private static final class StompSessionHandlerNoOp
            extends org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter {
    }
}
