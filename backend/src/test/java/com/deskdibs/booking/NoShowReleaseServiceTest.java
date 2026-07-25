package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.team.TeamMemberRepository;
import com.deskdibs.team.TeamRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 11:00 no-show release: seats held by people who never turned up go back into the pool.
 *
 * <p>Time is driven rather than waited for. The clock starts before the cut-off and the test moves
 * it past 11:00, so "released at the cut-off" is proven in milliseconds instead of by a suite that
 * only passes if it happens to run late morning.
 */
@Import(ControllableClockConfiguration.class)
class NoShowReleaseServiceTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final LocalTime BEFORE_CUTOFF = LocalTime.of(9, 0);
    private static final LocalTime AFTER_CUTOFF = LocalTime.of(11, 0);

    private final NoShowReleaseService releaseService;
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

    private long seatOne;
    private long seatTwo;

    NoShowReleaseServiceTest(NoShowReleaseService releaseService,
                             BookingService bookingService,
                             BookingRepository bookingRepository,
                             SeatRepository seatRepository,
                             SeatReservationRepository seatReservationRepository,
                             AppUserRepository appUserRepository,
                             TeamRepository teamRepository,
                             TeamMemberRepository teamMemberRepository,
                             MutableClock clock,
                             OfficeProperties office) {
        this.releaseService = releaseService;
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
        moveClockTo(TODAY, BEFORE_CUTOFF);
        bookingRepository.deleteAllInBatch();
        seatReservationRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();

        alice = person("alice@deskdibs.test", "Alice M.", UserRole.EMPLOYEE);
        bob = person("bob@deskdibs.test", "Bob T.", UserRole.EMPLOYEE);
        carol = person("carol@deskdibs.test", "Carol S.", UserRole.EMPLOYEE);

        seatOne = seatId("R2-A1");
        seatTwo = seatId("R2-A2");
    }

    @Test
    @DisplayName("a seat nobody checked into is released and immediately claimable by someone else")
    void anUncheckedBookingIsReleasedAndTheSeatBecomesClaimable() {
        BookingView alices = bookingService.claim(alice, seatOne, TODAY, null);

        moveClockTo(TODAY, AFTER_CUTOFF);
        int released = releaseService.releaseTodaysNoShows();

        assertThat(released).isEqualTo(1);
        assertThat(bookingRepository.findById(alices.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.RELEASED_NO_SHOW);

        // The point of RELEASED_NO_SHOW rather than DELETE: the row leaves the partial unique
        // index, so the seat is claimable again with no other state change anywhere.
        BookingView bobsNow = bookingService.claim(bob, seatOne, TODAY, null);
        assertThat(bobsNow.userId()).isEqualTo(bob);

        assertThat(bookingRepository.findAll())
                .as("the released row is kept as history, not deleted")
                .hasSize(2);
    }

    @Test
    @DisplayName("checking in keeps your seat when the release runs")
    void aCheckedInBookingSurvivesTheRelease() {
        BookingView alices = bookingService.claim(alice, seatOne, TODAY, null);
        bookingService.checkIn(alices.id(), alice);

        moveClockTo(TODAY, AFTER_CUTOFF);

        assertThat(releaseService.releaseTodaysNoShows()).isZero();
        assertThat(bookingRepository.findById(alices.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    @DisplayName("only the no-shows are released, and the people who turned up are left alone")
    void releasesOnlyTheBookingsNobodyCheckedInFor() {
        BookingView present = bookingService.claim(alice, seatOne, TODAY, null);
        BookingView absent = bookingService.claim(bob, seatTwo, TODAY, null);
        bookingService.checkIn(present.id(), alice);

        moveClockTo(TODAY, AFTER_CUTOFF);

        assertThat(releaseService.releaseTodaysNoShows()).isEqualTo(1);
        assertThat(bookingRepository.findById(present.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);
        assertThat(bookingRepository.findById(absent.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.RELEASED_NO_SHOW);
    }

    @Test
    @DisplayName("tomorrow's bookings are untouched — the release only ever acts on today")
    void doesNotTouchBookingsForOtherDates() {
        BookingView tomorrows = bookingService.claim(alice, seatOne, TODAY.plusDays(1), null);

        moveClockTo(TODAY, AFTER_CUTOFF);

        assertThat(releaseService.releaseTodaysNoShows()).isZero();
        assertThat(bookingRepository.findById(tomorrows.id()).orElseThrow().getStatus())
                .as("a seat booked for tomorrow must survive tonight, and every night before it")
                .isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    @DisplayName("running the release twice releases nothing the second time")
    void isIdempotentAcrossRepeatedRuns() {
        bookingService.claim(alice, seatOne, TODAY, null);
        bookingService.claim(bob, seatTwo, TODAY, null);

        moveClockTo(TODAY, AFTER_CUTOFF);

        assertThat(releaseService.releaseTodaysNoShows()).isEqualTo(2);
        assertThat(releaseService.releaseTodaysNoShows())
                .as("a retry after a failure, or a second instance firing, must be harmless")
                .isZero();
    }

    @Test
    @DisplayName("a seat re-claimed after being released is not released again by a later run")
    void doesNotReleaseASeatSomebodyClaimedAfterTheCutoff() {
        bookingService.claim(alice, seatOne, TODAY, null);

        moveClockTo(TODAY, AFTER_CUTOFF);
        releaseService.releaseTodaysNoShows();

        // Carol walks in at 11:30 and takes the freed seat.
        moveClockTo(TODAY, LocalTime.of(11, 30));
        BookingView carols = bookingService.claim(carol, seatOne, TODAY, null);
        bookingService.checkIn(carols.id(), carol);

        assertThat(releaseService.releaseTodaysNoShows()).isZero();
        assertThat(bookingRepository.findById(carols.id()).orElseThrow().getStatus())
                .as("she checked in, so a later run must leave her alone")
                .isEqualTo(BookingStatus.ACTIVE);
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────────

    private void moveClockTo(LocalDate day, LocalTime timeOfDay) {
        clock.setTo(ZonedDateTime.of(day, timeOfDay, office));
    }

    private long person(String email, String displayName, UserRole role) {
        return appUserRepository.saveAndFlush(new AppUser(email, displayName, role)).getId();
    }

    private Seat seat(String label) {
        return seatRepository.findByLabel(label).orElseThrow();
    }

    private long seatId(String label) {
        return seat(label).getId();
    }
}
