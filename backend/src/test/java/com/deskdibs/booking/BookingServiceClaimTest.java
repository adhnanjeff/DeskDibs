package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Claiming a seat, rule by rule, against real PostgreSQL.
 *
 * <p>Time is driven by a clock the test moves, so the 10:00 team-block release is exercised at
 * 09:00 and again at 10:30 without a single sleep and without caring when the suite runs.
 */
@Import(ControllableClockConfiguration.class)
class BookingServiceClaimTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final LocalTime BEFORE_RELEASE = LocalTime.of(9, 0);
    private static final LocalTime AFTER_RELEASE = LocalTime.of(10, 30);
    private static final int HORIZON_DAYS = 14;

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MutableClock clock;

    private final ZoneId office;

    private long alice;
    private long bob;
    private long carol;
    private long dana;

    private long freeSeat;
    private long heldSeat;
    private long anotherFreeSeat;

    BookingServiceClaimTest(BookingService bookingService,
                            BookingRepository bookingRepository,
                            SeatRepository seatRepository,
                            SeatReservationRepository seatReservationRepository,
                            AppUserRepository appUserRepository,
                            TeamRepository teamRepository,
                            TeamMemberRepository teamMemberRepository,
                            MutableClock clock,
                            OfficeProperties office) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.clock = clock;
        this.office = office.timezone();
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        moveClockTo(BEFORE_RELEASE);
        clearPeopleAndBookings();
        restoreEverySeat();

        alice = person("alice@deskdibs.test", "Alice M.", UserRole.EMPLOYEE);
        bob = person("bob@deskdibs.test", "Bob T.", UserRole.EMPLOYEE);
        carol = person("carol@deskdibs.test", "Carol P.", UserRole.EMPLOYEE);
        dana = person("dana@deskdibs.test", "Dana K.", UserRole.MANAGER);

        freeSeat = seatId("R1-A1");
        heldSeat = seatId("R1-A2");
        anotherFreeSeat = seatId("R1-A3");

        // Platform holds R1-A2 for three days; Carol is on the team, Alice and Bob are not.
        Team platform = teamRepository.saveAndFlush(new Team("Platform", user(dana)));
        teamMemberRepository.saveAndFlush(new TeamMember(platform, user(carol)));
        seatReservationRepository.saveAndFlush(
                new SeatReservation(seat("R1-A2"), platform, TODAY, TODAY.plusDays(2), user(dana)));
    }

    /**
     * Seats, teams and holds are shared state in a shared database. Other test classes clear only
     * bookings and people, and {@code seat_reservation.created_by} is ON DELETE RESTRICT, so
     * leaving a hold behind would break whichever class runs next.
     */
    @AfterEach
    void leaveTheOfficeAsItWasFound() {
        clearPeopleAndBookings();
        restoreEverySeat();
    }

    @Test
    @DisplayName("claiming a free seat creates exactly one ACTIVE booking")
    void claimingAFreeSeatCreatesExactlyOneActiveBooking() {
        BookingView booking = bookingService.claim(alice, freeSeat, TODAY, null);

        assertThat(booking.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(booking.seatId()).isEqualTo(freeSeat);
        assertThat(booking.seatLabel()).isEqualTo("R1-A1");
        assertThat(booking.userDisplayName()).isEqualTo("Alice M.");
        assertThat(booking.bookingDate()).isEqualTo(TODAY);
        assertThat(booking.checkedInAt()).isNull();

        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(freeSeat, TODAY, BookingStatus.ACTIVE))
                .isEqualTo(1);
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a date in the past is refused, and the refusal carries the allowed range")
    void aDateInThePastIsRefused() {
        LocalDate yesterday = TODAY.minusDays(1);

        assertThatThrownBy(() -> bookingService.claim(alice, freeSeat, yesterday, null))
                .isInstanceOfSatisfying(DateOutsideBookingWindowException.class, refusal -> {
                    assertThat(refusal.getRequestedDate()).isEqualTo(yesterday);
                    assertThat(refusal.getEarliestAllowed()).isEqualTo(TODAY);
                    assertThat(refusal.getLatestAllowed()).isEqualTo(TODAY.plusDays(HORIZON_DAYS));
                });

        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("the last day of the horizon is bookable and the day after it is not")
    void theLastDayOfTheHorizonIsBookableAndTheDayAfterItIsNot() {
        LocalDate lastAllowed = TODAY.plusDays(HORIZON_DAYS);
        LocalDate beyond = lastAllowed.plusDays(1);

        assertThat(bookingService.claim(alice, freeSeat, lastAllowed, null).status())
                .isEqualTo(BookingStatus.ACTIVE);

        assertThatThrownBy(() -> bookingService.claim(alice, freeSeat, beyond, null))
                .isInstanceOfSatisfying(DateOutsideBookingWindowException.class, refusal -> {
                    assertThat(refusal.getRequestedDate()).isEqualTo(beyond);
                    assertThat(refusal.getLatestAllowed()).isEqualTo(lastAllowed);
                });

        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a DISABLED seat is refused, naming the seat and why")
    void aDisabledSeatIsRefused() {
        Seat disabled = seat("R1-B1");
        disabled.setStatus(SeatStatus.DISABLED);
        seatRepository.saveAndFlush(disabled);

        assertThatThrownBy(() -> bookingService.claim(alice, disabled.getId(), TODAY, null))
                .isInstanceOfSatisfying(SeatNotBookableException.class, refusal -> {
                    assertThat(refusal.getSeatLabel()).isEqualTo("R1-B1");
                    assertThat(refusal.getSeatStatus()).isEqualTo(SeatStatus.DISABLED);
                });

        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("an outsider cannot take a team-held seat before the hold releases")
    void anOutsiderCannotTakeATeamHeldSeatBeforeTheHoldReleases() {
        moveClockTo(BEFORE_RELEASE);

        assertThatThrownBy(() -> bookingService.claim(bob, heldSeat, TODAY, null))
                .isInstanceOfSatisfying(SeatReservedForTeamException.class, refusal -> {
                    assertThat(refusal.getTeamName()).isEqualTo("Platform");
                    assertThat(refusal.getReleaseAtTime()).isEqualTo(LocalTime.of(10, 0));
                    assertThat(refusal.getSeatLabel()).isEqualTo("R1-A2");
                    assertThat(refusal.getBookingDate()).isEqualTo(TODAY);
                });

        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("the same outsider takes the same seat once the hold has released")
    void theSameOutsiderTakesTheSameSeatOnceTheHoldHasReleased() {
        assertThatThrownBy(() -> bookingService.claim(bob, heldSeat, TODAY, null))
                .isInstanceOf(SeatReservedForTeamException.class);

        // No job, no state change: only the clock moves past 10:00.
        moveClockTo(AFTER_RELEASE);

        BookingView booking = bookingService.claim(bob, heldSeat, TODAY, null);

        assertThat(booking.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(booking.seatLabel()).isEqualTo("R1-A2");
        assertThat(seatReservationRepository.findAll())
                .as("the hold is soft — it is still on file, it just stopped being enforced")
                .hasSize(1);
    }

    @Test
    @DisplayName("a member of the holding team takes their seat before the hold releases")
    void aMemberOfTheHoldingTeamTakesTheirSeatBeforeTheHoldReleases() {
        moveClockTo(BEFORE_RELEASE);

        BookingView booking = bookingService.claim(carol, heldSeat, TODAY, null);

        assertThat(booking.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(booking.userDisplayName()).isEqualTo("Carol P.");
        assertThat(booking.seatLabel()).isEqualTo("R1-A2");
    }

    @Test
    @DisplayName("a hold on a future date is still enforced after 10:00 today")
    void aHoldOnAFutureDateIsStillEnforcedAfterTenThisMorning() {
        moveClockTo(AFTER_RELEASE);

        // Today's hold has released, but tomorrow's has not: it releases at 10:00 tomorrow.
        assertThat(bookingService.claim(bob, heldSeat, TODAY, null).status()).isEqualTo(BookingStatus.ACTIVE);
        assertThatThrownBy(() -> bookingService.claim(alice, heldSeat, TODAY.plusDays(1), null))
                .isInstanceOf(SeatReservedForTeamException.class);
    }

    @Test
    @DisplayName("a second seat on a day you already booked is refused, naming the seat you hold")
    void aSecondSeatOnADayYouAlreadyBookedIsRefused() {
        bookingService.claim(alice, freeSeat, TODAY, null);

        assertThatThrownBy(() -> bookingService.claim(alice, anotherFreeSeat, TODAY, null))
                .isInstanceOfSatisfying(AlreadyBookedThatDayException.class, refusal -> {
                    assertThat(refusal.getExistingSeatLabel()).isEqualTo("R1-A1");
                    assertThat(refusal.getExistingSeatId()).isEqualTo(freeSeat);
                    assertThat(refusal.getBookingDate()).isEqualTo(TODAY);
                });

        assertThat(bookingRepository.countByUserIdAndBookingDateAndStatus(alice, TODAY, BookingStatus.ACTIVE))
                .isEqualTo(1);
        assertThat(bookingRepository.findAll())
                .as("the losing insert left nothing behind")
                .hasSize(1);
    }

    @Test
    @DisplayName("replaying an idempotency key returns the original booking and creates no second row")
    void replayingAnIdempotencyKeyReturnsTheOriginalBooking() {
        String key = "claim-7f3c1a";

        BookingView first = bookingService.claim(alice, freeSeat, TODAY, key);
        BookingView replay = bookingService.claim(alice, freeSeat, TODAY, key);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(bookingRepository.findAll())
                .as("a retry is not a second booking")
                .hasSize(1);
    }

    @Test
    @DisplayName("reusing one idempotency key for a different seat is refused rather than silently replayed")
    void reusingOneIdempotencyKeyForADifferentSeatIsRefused() {
        String key = "claim-7f3c1a";
        BookingView original = bookingService.claim(alice, freeSeat, TODAY, key);

        assertThatThrownBy(() -> bookingService.claim(alice, anotherFreeSeat, TODAY, key))
                .isInstanceOfSatisfying(IdempotencyKeyConflictException.class, refusal -> {
                    assertThat(refusal.getIdempotencyKey()).isEqualTo(key);
                    assertThat(refusal.getOriginalBooking().id()).isEqualTo(original.id());
                    assertThat(refusal.getRequestedSeatId()).isEqualTo(anotherFreeSeat);
                });

        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private void moveClockTo(LocalTime timeOfDay) {
        clock.setTo(ZonedDateTime.of(TODAY, timeOfDay, office));
    }

    private void clearPeopleAndBookings() {
        bookingRepository.deleteAllInBatch();
        seatReservationRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }

    /** The 110 seeded seats are shared by every test class; put back anything this one changed. */
    private void restoreEverySeat() {
        List<Seat> changed = seatRepository.findAll().stream()
                .filter(seat -> seat.getStatus() != SeatStatus.ACTIVE)
                .peek(seat -> seat.setStatus(SeatStatus.ACTIVE))
                .toList();
        if (!changed.isEmpty()) {
            seatRepository.saveAllAndFlush(changed);
        }
    }

    private long person(String email, String displayName, UserRole role) {
        return appUserRepository.saveAndFlush(new AppUser(email, displayName, role)).getId();
    }

    private AppUser user(long id) {
        return appUserRepository.findById(id).orElseThrow();
    }

    private Seat seat(String label) {
        return seatRepository.findByLabel(label).orElseThrow();
    }

    private long seatId(String label) {
        return seat(label).getId();
    }
}
