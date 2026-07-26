package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.seat.SeatStatus;
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
        clock.setTo(TODAY.atTime(9, 0).atZone(office.timezone()));
    }

    @BeforeEach
    void resetTheOfficeAndItsPeople() {
        bookingRepository.deleteAllInBatch();
        seatReservationRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        restoreEverySeat();

        alice = person("alice@deskdibs.test", "Alice M.");
        bob = person("bob@deskdibs.test", "Bob T.");
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
        assertThat(new DayAvailabilityView(TODAY, 2, 5, null, true).freeSeats()).isZero();
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

    private long seatId(String label) {
        return seat(label).getId();
    }
}
