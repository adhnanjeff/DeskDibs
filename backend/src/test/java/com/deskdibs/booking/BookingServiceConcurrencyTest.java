package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.team.TeamMemberRepository;
import com.deskdibs.team.TeamRepository;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headline test, driven through the service rather than the repository.
 *
 * <p>{@code ConcurrentSeatClaimTest} proves the database refuses 149 of 150 inserts. This proves
 * the booking engine turns each of those refusals into something a person can act on: exactly one
 * winner, 149 {@link SeatAlreadyBookedException}s that all name the winner, and not one raw
 * {@code DataIntegrityViolationException} escaping the service.
 *
 * <p>Every claimant is a different person, so the only constraint in play is
 * {@code uq_seat_active_per_date}. A latch releases all 150 threads at once — no sleeps, no
 * staggering, real contention.
 */
@Import(ControllableClockConfiguration.class)
class BookingServiceConcurrencyTest extends AbstractPostgresIntegrationTest {

    private static final int CONCURRENT_CLAIMS = 150;
    private static final String CONTESTED_SEAT = "R3-A2";
    private static final LocalDate DATE = ControllableClockConfiguration.DEFAULT_TODAY;

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private long contestedSeatId;
    private List<Long> claimantIds;

    BookingServiceConcurrencyTest(BookingService bookingService,
                                  BookingRepository bookingRepository,
                                  SeatRepository seatRepository,
                                  SeatReservationRepository seatReservationRepository,
                                  AppUserRepository appUserRepository,
                                  TeamRepository teamRepository,
                                  TeamMemberRepository teamMemberRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @BeforeEach
    void lineUpOneHundredAndFiftyClaimants() {
        clearPeopleAndBookings();

        contestedSeatId = seatRepository.findByLabel(CONTESTED_SEAT).orElseThrow().getId();

        List<AppUser> claimants = new ArrayList<>(CONCURRENT_CLAIMS);
        for (int i = 0; i < CONCURRENT_CLAIMS; i++) {
            claimants.add(new AppUser("claimant-%03d@deskdibs.test".formatted(i),
                    "Claimant %03d".formatted(i), UserRole.EMPLOYEE));
        }
        claimantIds = appUserRepository.saveAllAndFlush(claimants).stream().map(AppUser::getId).toList();
    }

    @AfterEach
    void clearUpAfterTheRace() {
        clearPeopleAndBookings();
    }

    @Test
    @DisplayName("150 people claim one seat at once: one booking, 149 refusals that name the winner")
    void oneHundredAndFiftySimultaneousClaimsProduceOneWinnerAndAConflictNamingThem()
            throws InterruptedException {

        Queue<BookingView> winners = new ConcurrentLinkedQueue<>();
        Queue<SeatAlreadyBookedException> losers = new ConcurrentLinkedQueue<>();
        Queue<Throwable> unexpectedFailures = new ConcurrentLinkedQueue<>();

        CountDownLatch atTheStartLine = new CountDownLatch(CONCURRENT_CLAIMS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONCURRENT_CLAIMS);
        ExecutorService threads = Executors.newFixedThreadPool(CONCURRENT_CLAIMS);

        try {
            for (Long claimantId : claimantIds) {
                threads.execute(() -> {
                    atTheStartLine.countDown();
                    try {
                        startGun.await();
                        winners.add(bookingService.claim(claimantId, contestedSeatId, DATE, null));
                    } catch (SeatAlreadyBookedException lostTheRace) {
                        losers.add(lostTheRace);
                    } catch (Throwable unexpected) {
                        unexpectedFailures.add(unexpected);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            assertThat(atTheStartLine.await(30, TimeUnit.SECONDS))
                    .as("all %d threads should be ready before the race starts", CONCURRENT_CLAIMS)
                    .isTrue();
            startGun.countDown();
            assertThat(finished.await(2, TimeUnit.MINUTES)).as("every claim should settle").isTrue();
        } finally {
            threads.shutdownNow();
        }

        assertThat(unexpectedFailures)
                .as("a lost race is a domain refusal; no raw DataIntegrityViolationException "
                        + "and no 500-shaped failure may leak out of the service")
                .isEmpty();
        assertThat(winners).as("winners").hasSize(1);
        assertThat(losers).as("losers").hasSize(CONCURRENT_CLAIMS - 1);

        BookingView winner = winners.element();
        assertThat(losers).allSatisfy(refusal -> {
            assertThat(refusal.errorCode()).isEqualTo(BookingErrorCode.SEAT_ALREADY_BOOKED);
            assertThat(refusal.getSeatLabel()).isEqualTo(CONTESTED_SEAT);
            assertThat(refusal.getBookingDate()).isEqualTo(DATE);
            assertThat(refusal.getTakenByUserId())
                    .as("the loser is told who actually holds the seat")
                    .isEqualTo(winner.userId());
            assertThat(refusal.getTakenByDisplayName()).isEqualTo(winner.userDisplayName());
        });

        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(
                contestedSeatId, DATE, BookingStatus.ACTIVE))
                .as("the seat is held exactly once")
                .isEqualTo(1);
        assertThat(bookingRepository.findAll())
                .as("the 149 losing inserts left no rows behind")
                .hasSize(1);
    }

    private void clearPeopleAndBookings() {
        bookingRepository.deleteAllInBatch();
        seatReservationRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }
}
