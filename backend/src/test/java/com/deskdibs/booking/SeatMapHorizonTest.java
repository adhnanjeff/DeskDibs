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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The date strip's data (PLAN.md §7): how full each of the next fourteen days is, and which of
 * them you have already claimed a desk on.
 */
@Import(ControllableClockConfiguration.class)
class SeatMapHorizonTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;

    private final SeatMapService seatMapService;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final int horizonDays;
    private final MutableClock clock;
    private final OfficeProperties office;

    private long alice;
    private long bob;

    SeatMapHorizonTest(SeatMapService seatMapService,
                       BookingService bookingService,
                       BookingRepository bookingRepository,
                       SeatRepository seatRepository,
                       SeatReservationRepository seatReservationRepository,
                       AppUserRepository appUserRepository,
                       TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       MutableClock clock,
                       OfficeProperties office) {
        this.seatMapService = seatMapService;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.horizonDays = office.bookingHorizonDays();
        this.clock = clock;
        this.office = office;
        clock.setTo(TODAY.atTime(9, 0).atZone(office.timezone()));
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        // Back to 09:00, before every cut-off — otherwise a test that moves the clock past the
        // same-day cut-off would silently change what "today" means for the next one.
        clock.setTo(TODAY.atTime(9, 0).atZone(office.timezone()));
        bookingRepository.deleteAllInBatch();
        seatReservationRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        restoreEverySeat();

        alice = person("alice@deskdibs.test", "Alice M.");
        bob = person("bob@deskdibs.test", "Bob T.");
    }

    /**
     * Holds would otherwise outlive this class. {@code @BeforeEach} only promises a clean slate to
     * the next test in <em>this</em> class; the database is shared with every other one, and a
     * leftover {@code seat_reservation} both takes a desk off the map they build and — through
     * {@code fk_seat_reservation_creator}, which is RESTRICT — stops them deleting the user that
     * created it.
     */
    @AfterEach
    void leaveNoHoldsBehind() {
        seatReservationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("returns one entry per bookable day, today first, with no gaps")
    void coversTheWholeHorizonOneDayAtATime() {
        List<DayAvailabilityView> horizon = horizon(alice);

        assertThat(horizon).hasSize(horizonDays + 1);
        assertThat(horizon.get(0).date()).isEqualTo(TODAY);
        assertThat(horizon.get(horizon.size() - 1).date()).isEqualTo(TODAY.plusDays(horizonDays));
        assertThat(horizon).extracting(DayAvailabilityView::date).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("counts every booking on a day, not just the caller's own")
    void countsEverybodysBookings() {
        bookingService.claim(alice, seatId("R2-A1"), TODAY, null);
        bookingService.claim(bob, seatId("R2-A2"), TODAY, null);
        bookingService.claim(alice, seatId("R2-A1"), TODAY.plusDays(1), null);

        List<DayAvailabilityView> horizon = horizon(alice);

        assertThat(horizon.get(0).bookedSeats()).isEqualTo(2);
        assertThat(horizon.get(1).bookedSeats()).isEqualTo(1);
        assertThat(horizon.get(2).bookedSeats()).isZero();
    }

    @Test
    @DisplayName("marks the days you already hold a desk, and names which desk")
    void namesYourOwnSeatOnTheDaysYouHaveOne() {
        bookingService.claim(alice, seatId("R2-A1"), TODAY, null);
        bookingService.claim(bob, seatId("R2-A2"), TODAY.plusDays(1), null);

        List<DayAvailabilityView> horizon = horizon(alice);

        assertThat(horizon.get(0).yourSeatLabel()).isEqualTo("R2-A1");
        assertThat(horizon.get(1).yourSeatLabel())
                .as("Bob's booking is somebody else's, and must not be shown as Alice's")
                .isNull();
    }

    @Test
    @DisplayName("a released booking stops counting against the day")
    void cancelledBookingsAreNotCounted() {
        BookingView held = bookingService.claim(alice, seatId("R2-A1"), TODAY, null);
        assertThat(horizon(alice).get(0).bookedSeats()).isEqualTo(1);

        bookingService.cancel(held.id(), alice);

        assertThat(horizon(alice).get(0).bookedSeats()).isZero();
        assertThat(horizon(alice).get(0).yourSeatLabel()).isNull();
    }

    @Test
    @DisplayName("an out-of-service desk is not counted as capacity")
    void disabledSeatsLeaveTheDenominator() {
        int before = horizon(alice).get(0).bookableSeats();

        Seat broken = seat("R2-A1");
        broken.setStatus(SeatStatus.BROKEN);
        seatRepository.saveAndFlush(broken);

        assertThat(horizon(alice).get(0).bookableSeats()).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("free desks never go negative, however the numbers move")
    void freeSeatsIsNeverNegative() {
        DayAvailabilityView day = horizon(alice).get(0);
        assertThat(day.freeSeats()).isEqualTo(day.bookableSeats() - day.bookedSeats());
        assertThat(new DayAvailabilityView(TODAY, 2, 5, 0, null, true, true).freeSeats()).isZero();
        assertThat(new DayAvailabilityView(TODAY, 2, 1, 4, null, true, true).freeSeats()).isZero();
    }

    // ─── Team holds against the strip's free count ───────────────────────────────

    @Test
    @DisplayName("a team hold takes its desks off the day's free count, for every day it covers")
    void heldDesksAreNotCountedAsFree() {
        Team platform = teamRepository.saveAndFlush(new Team("Platform", user(alice)));
        hold(platform, "R2-A1", TODAY, TODAY.plusDays(1));
        hold(platform, "R2-A2", TODAY, TODAY);

        List<DayAvailabilityView> horizon = horizon(alice);
        int bookable = horizon.get(0).bookableSeats();

        assertThat(horizon.get(0).heldSeats()).isEqualTo(2);
        assertThat(horizon.get(0).freeSeats()).isEqualTo(bookable - 2);
        assertThat(horizon.get(1).heldSeats()).as("the two-day hold still applies tomorrow").isEqualTo(1);
        assertThat(horizon.get(2).heldSeats()).as("and stops the day after it ends").isZero();
    }

    @Test
    @DisplayName("a desk that is both held and booked is one desk gone, not two")
    void aBookedHeldDeskIsCountedOnce() {
        Team platform = teamRepository.saveAndFlush(new Team("Platform", user(alice)));
        hold(platform, "R2-A1", TODAY, TODAY);
        hold(platform, "R2-A2", TODAY, TODAY);
        // Alice is on Platform, so she is allowed to claim into her own team's block.
        teamMemberRepository.saveAndFlush(new TeamMember(platform, user(alice)));
        bookingService.claim(alice, seatId("R2-A1"), TODAY, null);

        DayAvailabilityView today = horizon(alice).get(0);

        assertThat(today.bookedSeats()).isEqualTo(1);
        assertThat(today.heldSeats()).as("R2-A1 is already counted as booked").isEqualTo(1);
        assertThat(today.freeSeats()).isEqualTo(today.bookableSeats() - 2);
    }

    @Test
    @DisplayName("past its release time a hold stops being counted, exactly as the map stops drawing it")
    void aLapsedHoldGivesItsDeskBack() {
        Team platform = teamRepository.saveAndFlush(new Team("Platform", user(alice)));
        SeatReservation held = hold(platform, "R2-A1", TODAY, TODAY);

        assertThat(horizon(alice).get(0).heldSeats()).isEqualTo(1);

        clock.setTo(TODAY.atTime(held.getReleaseAtTime().plusMinutes(1)).atZone(office.timezone()));

        assertThat(horizon(alice).get(0).heldSeats())
                .as("the soft release is a clock comparison, so no job has to have run")
                .isZero();
        assertThat(horizon(alice).get(1).heldSeats())
                .as("and it never applied to tomorrow anyway")
                .isZero();
    }

    @Test
    @DisplayName("a hold on an out-of-service desk does not take the same desk away twice")
    void holdsOnDisabledDesksAreIgnored() {
        Team platform = teamRepository.saveAndFlush(new Team("Platform", user(alice)));
        hold(platform, "R2-A1", TODAY, TODAY);

        Seat broken = seat("R2-A1");
        broken.setStatus(SeatStatus.BROKEN);
        seatRepository.saveAndFlush(broken);

        DayAvailabilityView today = horizon(alice).get(0);

        assertThat(today.heldSeats()).as("it already left the denominator").isZero();
        assertThat(today.freeSeats()).isEqualTo(today.bookableSeats());
    }

    @Test
    @DisplayName("weekends are listed but marked closed, so the fortnight reads without gaps")
    void weekendsAreShownAndMarkedUnbookable() {
        List<DayAvailabilityView> horizon = horizon(alice);

        // DEFAULT_TODAY is a Monday, so the sixth and seventh entries are the weekend.
        assertThat(horizon.get(0).bookable()).as("Monday").isTrue();
        assertThat(horizon.get(4).bookable()).as("Friday").isTrue();
        assertThat(horizon.get(5).bookable()).as("Saturday").isFalse();
        assertThat(horizon.get(6).bookable()).as("Sunday").isFalse();
        assertThat(horizon.get(7).bookable()).as("the following Monday").isTrue();

        assertThat(horizon)
                .as("closed days stay in the list rather than being dropped out of it")
                .hasSize(horizonDays + 1);
    }

    @Test
    @DisplayName("a desk cannot be booked for a day the office is shut, however the request arrives")
    void claimingOnAClosedDayIsRefused() {
        LocalDate saturday = TODAY.plusDays(5);

        assertThatThrownBy(() -> bookingService.claim(alice, seatId("R2-A1"), saturday, null))
                .isInstanceOf(DateNotAWorkingDayException.class);

        assertThat(bookingRepository.count())
                .as("nothing was written for a day nobody is in the office")
                .isZero();
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────────


    @Test
    @DisplayName("before the same-day cut-off the strip still offers today")
    void offersTodayBeforeTheCutoff() {
        clock.setTo(TODAY.atTime(office.sameDayCutoffTime().minusMinutes(1)).atZone(office.timezone()));

        List<DayAvailabilityView> strip = seatMapService.bookableHorizon(alice);

        assertThat(strip.get(0).date()).isEqualTo(TODAY);
        assertThat(strip).hasSize(horizonDays + 1);
    }

    @Test
    @DisplayName("past the same-day cut-off the strip starts at tomorrow instead")
    void dropsTodayAfterTheCutoff() {
        clock.setTo(TODAY.atTime(office.sameDayCutoffTime().plusMinutes(1)).atZone(office.timezone()));

        List<DayAvailabilityView> strip = seatMapService.bookableHorizon(alice);

        assertThat(strip.get(0).date()).isEqualTo(TODAY.plusDays(1));
        assertThat(strip).extracting(DayAvailabilityView::date).doesNotContain(TODAY);
    }

    @Test
    @DisplayName("passing the cut-off does not extend the far end of the horizon")
    void theEndOfTheHorizonDoesNotMove() {
        clock.setTo(TODAY.atTime(office.sameDayCutoffTime().plusHours(3)).atZone(office.timezone()));

        List<DayAvailabilityView> strip = seatMapService.bookableHorizon(alice);

        assertThat(strip.get(strip.size() - 1).date()).isEqualTo(TODAY.plusDays(horizonDays));
        assertThat(strip).hasSize(horizonDays);
    }

    private List<DayAvailabilityView> horizon(long userId) {
        return seatMapService.availabilityHorizon(userId, TODAY, TODAY.plusDays(horizonDays));
    }

    private void restoreEverySeat() {
        List<Seat> changed = seatRepository.findAll().stream()
                .filter(seat -> seat.getStatus() != SeatStatus.ACTIVE)
                .peek(seat -> seat.setStatus(SeatStatus.ACTIVE))
                .toList();
        if (!changed.isEmpty()) {
            seatRepository.saveAllAndFlush(changed);
        }
    }

    private long person(String email, String displayName) {
        return appUserRepository.saveAndFlush(new AppUser(email, displayName, UserRole.EMPLOYEE)).getId();
    }

    private Seat seat(String label) {
        return seatRepository.findByLabel(label).orElseThrow();
    }

    private AppUser user(long id) {
        return appUserRepository.findById(id).orElseThrow();
    }

    /** A block on one desk, with the default release time the manager UI would give it. */
    private SeatReservation hold(Team team, String label, LocalDate from, LocalDate to) {
        return seatReservationRepository
                .saveAndFlush(new SeatReservation(seat(label), team, from, to, user(alice)));
    }

    private long seatId(String label) {
        return seat(label).getId();
    }
}
