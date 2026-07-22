package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headline test of the whole design.
 *
 * <p>150 people claim the same seat on the same date at the same instant. Exactly one wins; the
 * other 149 lose on the unique index, cleanly, with no unexpected failure — and afterwards the
 * database holds exactly one ACTIVE booking for that seat and date.
 *
 * <p>Every attempt uses a <em>different</em> person, so the only constraint in play is
 * {@code uq_seat_active_per_date}. There is no lock, no queue, and no {@code SELECT ... FOR UPDATE}
 * pre-check anywhere in this path: the losing inserts are supposed to fail on the constraint.
 * A {@link CountDownLatch} releases every thread at once rather than staggering them with sleeps,
 * so the contention is real.
 */
class ConcurrentSeatClaimTest extends AbstractPostgresIntegrationTest {

    private static final int CONCURRENT_CLAIMS = 150;
    private static final String CONTESTED_SEAT = "R3-A2";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 10);

    private final BookingRepository bookingRepository;
    private final AppUserRepository appUserRepository;
    private final SeatRepository seatRepository;
    private final TransactionTemplate transactionTemplate;

    private Long contestedSeatId;
    private List<Long> claimantIds;

    ConcurrentSeatClaimTest(BookingRepository bookingRepository,
                            AppUserRepository appUserRepository,
                            SeatRepository seatRepository,
                            TransactionTemplate transactionTemplate) {
        this.bookingRepository = bookingRepository;
        this.appUserRepository = appUserRepository;
        this.seatRepository = seatRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @BeforeEach
    void resetBookingsAndPeople() {
        bookingRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();

        contestedSeatId = seatRepository.findByLabel(CONTESTED_SEAT).orElseThrow().getId();

        List<AppUser> claimants = new ArrayList<>(CONCURRENT_CLAIMS);
        for (int i = 0; i < CONCURRENT_CLAIMS; i++) {
            claimants.add(new AppUser("claimant-%03d@deskdibs.test".formatted(i), "Claimant " + i, UserRole.EMPLOYEE));
        }
        claimantIds = appUserRepository.saveAllAndFlush(claimants).stream().map(AppUser::getId).toList();
    }

    @Test
    @DisplayName("150 people claiming one seat at once: exactly one wins and 149 lose on the constraint")
    void oneHundredAndFiftySimultaneousClaimsLeaveExactlyOneWinner() throws InterruptedException {
        AtomicInteger won = new AtomicInteger();
        Queue<String> lostOnTheConstraint = new ConcurrentLinkedQueue<>();
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
                        claimContestedSeat(claimantId);
                        won.incrementAndGet();
                    } catch (DataIntegrityViolationException expected) {
                        lostOnTheConstraint.add(String.valueOf(expected.getMessage()));
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
            assertThat(finished.await(2, TimeUnit.MINUTES))
                    .as("every claim should settle")
                    .isTrue();
        } finally {
            threads.shutdownNow();
        }

        assertThat(unexpectedFailures)
                .as("losing a race must be a clean constraint violation, never an unexpected error")
                .isEmpty();
        assertThat(won.get()).as("winners").isEqualTo(1);
        assertThat(lostOnTheConstraint).as("losers").hasSize(CONCURRENT_CLAIMS - 1);
        assertThat(lostOnTheConstraint)
                .as("every loser lost on the seat guard — each claimant is a different person, "
                        + "so the per-person guard is not what stopped them")
                .allMatch(message -> message.contains("uq_seat_active_per_date"));

        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(
                contestedSeatId, DATE, BookingStatus.ACTIVE))
                .as("the seat is held exactly once")
                .isEqualTo(1);
        assertThat(bookingRepository.findAll())
                .as("the 149 losing inserts left no rows behind")
                .hasSize(1);
    }

    private void claimContestedSeat(Long claimantId) {
        transactionTemplate.executeWithoutResult(status -> bookingRepository.saveAndFlush(
                new Booking(seatRepository.getReferenceById(contestedSeatId),
                        appUserRepository.getReferenceById(claimantId), DATE, null)));
    }
}
