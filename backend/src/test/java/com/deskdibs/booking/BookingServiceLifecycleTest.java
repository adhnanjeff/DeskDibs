package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import com.deskdibs.common.MutableClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.seat.SeatStatus;
import com.deskdibs.team.Team;
import com.deskdibs.team.TeamMember;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What happens to a booking after it exists: cancelling it, moving it, and turning up for it.
 *
 * <p>The claim rules themselves live in {@link BookingServiceClaimTest}; this class is about the
 * three operations that act on a booking already in the database, and about who is allowed to
 * perform them. Time comes from a clock the test moves, so "only today's booking can be checked
 * into" is exercised by changing the date rather than by caring when the suite runs.
 */
@Import(ControllableClockConfiguration.class)
class BookingServiceLifecycleTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate TODAY = ControllableClockConfiguration.DEFAULT_TODAY;
    private static final LocalTime MORNING = LocalTime.of(9, 0);

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
    private long dana;
    private long root;

    private long seatOne;
    private long seatTwo;

    BookingServiceLifecycleTest(BookingService bookingService,
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
        moveClockTo(TODAY, MORNING);
        clearPeopleAndBookings();
        restoreEverySeat();

        alice = person("alice@deskdibs.test", "Alice M.", UserRole.EMPLOYEE);
        bob = person("bob@deskdibs.test", "Bob T.", UserRole.EMPLOYEE);
        dana = person("dana@deskdibs.test", "Dana K.", UserRole.MANAGER);
        root = person("root@deskdibs.test", "Root A.", UserRole.ADMIN);

        seatOne = seatId("R2-A1");
        seatTwo = seatId("R2-A2");

        // Dana manages Platform; Alice is on it. Bob is not, and is nobody's report.
        Team platform = teamRepository.saveAndFlush(new Team("Platform", user(dana)));
        teamMemberRepository.saveAndFlush(new TeamMember(platform, user(alice)));
    }

    // ─── Cancel ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelling your own booking frees the seat for somebody else immediately")
    void cancellingReleasesTheSeatForImmediateRebooking() {
        BookingView held = bookingService.claim(alice, seatOne, TODAY, null);

        BookingView cancelled = bookingService.cancel(held.id(), alice);

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);

        // The whole point of CANCELLED rather than DELETE: the row leaves the partial unique
        // index, so the seat is claimable again without waiting for anything.
        BookingView bobsNow = bookingService.claim(bob, seatOne, TODAY, null);
        assertThat(bobsNow.userId()).isEqualTo(bob);
        assertThat(bobsNow.seatLabel()).isEqualTo("R2-A1");

        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(
                seatOne, TODAY, BookingStatus.ACTIVE))
                .as("exactly one live booking on the seat, and it is Bob's")
                .isEqualTo(1);
        assertThat(bookingRepository.findAll())
                .as("the cancelled row is kept as history, not deleted")
                .hasSize(2);
    }

    @Test
    @DisplayName("an unrelated colleague cannot cancel your booking even knowing its id")
    void anUnrelatedUserCannotCancelSomebodyElsesBooking() {
        BookingView alices = bookingService.claim(alice, seatOne, TODAY, null);

        assertThatThrownBy(() -> bookingService.cancel(alices.id(), bob))
                .isInstanceOf(BookingAccessDeniedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        BookingAccessDeniedException.class))
                .satisfies(denied -> {
                    assertThat(denied.getAction()).isEqualTo(BookingAccessDeniedException.Action.CANCEL);
                    assertThat(denied.getActingUserId()).isEqualTo(bob);
                    assertThat(denied.getOwnerUserId()).isEqualTo(alice);
                });

        assertThat(bookingRepository.findById(alices.id()).orElseThrow().getStatus())
                .as("the booking survives the refused attempt")
                .isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    @DisplayName("the manager of the owner's team, and an admin, may both cancel on their behalf")
    void managerOfTheOwningTeamAndAdminMayCancel() {
        BookingView byManager = bookingService.claim(alice, seatOne, TODAY, null);
        assertThat(bookingService.cancel(byManager.id(), dana).status())
                .as("Dana manages Platform and Alice is on it")
                .isEqualTo(BookingStatus.CANCELLED);

        BookingView byAdmin = bookingService.claim(alice, seatTwo, TODAY, null);
        assertThat(bookingService.cancel(byAdmin.id(), root).status())
                .as("an ADMIN may act on any booking")
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelling an already-cancelled booking is refused rather than silently repeated")
    void cancellingTwiceIsRefused() {
        BookingView held = bookingService.claim(alice, seatOne, TODAY, null);
        bookingService.cancel(held.id(), alice);

        assertThatThrownBy(() -> bookingService.cancel(held.id(), alice))
                .isInstanceOf(BookingNotActiveException.class);
    }

    // ─── Move ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("moving seats releases the old one and takes the new one, leaving one live booking")
    void movingSwapsTheSeatAtomically() {
        BookingView original = bookingService.claim(alice, seatOne, TODAY, null);

        BookingView moved = bookingService.move(alice, seatTwo, TODAY, null);

        assertThat(moved.seatLabel()).isEqualTo("R2-A2");
        assertThat(moved.id()).as("a move is a new booking, not an edit").isNotEqualTo(original.id());

        assertThat(bookingRepository.findById(original.id()).orElseThrow().getStatus())
                .as("the seat she left is released")
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(
                seatOne, TODAY, BookingStatus.ACTIVE))
                .isEqualTo(0);
        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(
                seatTwo, TODAY, BookingStatus.ACTIVE))
                .isEqualTo(1);
    }

    /**
     * The failure that would hurt most: reaching for a seat somebody else already has, and losing
     * the one you were sitting in as a side effect. The move is one transaction precisely so the
     * cancel rolls back with the failed insert.
     */
    @Test
    @DisplayName("a move to a taken seat leaves your original booking untouched and ACTIVE")
    void aFailedMoveDoesNotCostYouTheSeatYouAlreadyHad() {
        BookingView alices = bookingService.claim(alice, seatOne, TODAY, null);
        bookingService.claim(bob, seatTwo, TODAY, null);

        assertThatThrownBy(() -> bookingService.move(alice, seatTwo, TODAY, null))
                .isInstanceOf(SeatAlreadyBookedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        SeatAlreadyBookedException.class))
                .satisfies(taken -> assertThat(taken.getTakenByDisplayName()).isEqualTo("Bob T."));

        assertThat(bookingRepository.findById(alices.id()).orElseThrow().getStatus())
                .as("the rollback put Alice's original booking back")
                .isEqualTo(BookingStatus.ACTIVE);
        assertThat(bookingRepository.countBySeatIdAndBookingDateAndStatus(
                seatOne, TODAY, BookingStatus.ACTIVE))
                .as("she is still sitting in R2-A1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("moving to the seat you already hold changes nothing")
    void movingToYourOwnSeatIsANoOp() {
        BookingView original = bookingService.claim(alice, seatOne, TODAY, null);

        BookingView same = bookingService.move(alice, seatOne, TODAY, null);

        assertThat(same.id()).isEqualTo(original.id());
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("moving when you hold nothing that day is just a claim")
    void movingWithNothingHeldBehavesAsAClaim() {
        BookingView claimed = bookingService.move(alice, seatOne, TODAY, null);

        assertThat(claimed.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(claimed.seatLabel()).isEqualTo("R2-A1");
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    // ─── Check-in ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checking in stamps the arrival time on your own booking")
    void ownerCanCheckIn() {
        BookingView held = bookingService.claim(alice, seatOne, TODAY, null);

        BookingView arrived = bookingService.checkIn(held.id(), alice);

        assertThat(arrived.checkedInAt()).isNotNull();
        assertThat(bookingRepository.findById(held.id()).orElseThrow().getCheckedInAt())
                .as("persisted, not just returned")
                .isNotNull();
    }

    /**
     * Deliberately stricter than cancel: a manager vouching for somebody's attendance would defeat
     * the 11:00 no-show release, which exists to put unused seats back into a pool of only 110.
     */
    @Test
    @DisplayName("nobody can check in on your behalf, not even your manager or an admin")
    void onlyTheOwnerMayCheckIn() {
        BookingView alices = bookingService.claim(alice, seatOne, TODAY, null);

        for (long impostor : List.of(bob, dana, root)) {
            assertThatThrownBy(() -> bookingService.checkIn(alices.id(), impostor))
                    .isInstanceOf(BookingAccessDeniedException.class)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                            BookingAccessDeniedException.class))
                    .satisfies(denied -> assertThat(denied.getAction())
                            .isEqualTo(BookingAccessDeniedException.Action.CHECK_IN));
        }

        assertThat(bookingRepository.findById(alices.id()).orElseThrow().getCheckedInAt())
                .as("no impostor left a mark")
                .isNull();
    }

    @Test
    @DisplayName("checking in twice is refused and does not overwrite the first arrival time")
    void checkingInTwiceIsRefused() {
        BookingView held = bookingService.claim(alice, seatOne, TODAY, null);
        BookingView first = bookingService.checkIn(held.id(), alice);

        assertThatThrownBy(() -> bookingService.checkIn(held.id(), alice))
                .isInstanceOf(AlreadyCheckedInException.class);

        assertThat(bookingRepository.findById(held.id()).orElseThrow().getCheckedInAt())
                .isEqualTo(first.checkedInAt());
    }

    @Test
    @DisplayName("tomorrow's booking cannot be checked into today, and can be once the day arrives")
    void onlyTodaysBookingCanBeCheckedInto() {
        LocalDate tomorrow = TODAY.plusDays(1);
        BookingView forTomorrow = bookingService.claim(alice, seatOne, tomorrow, null);

        assertThatThrownBy(() -> bookingService.checkIn(forTomorrow.id(), alice))
                .isInstanceOf(CheckInNotForTodayException.class);

        // Same booking, same person — only the office clock moved.
        moveClockTo(tomorrow, MORNING);
        assertThat(bookingService.checkIn(forTomorrow.id(), alice).checkedInAt()).isNotNull();
    }

    @Test
    @DisplayName("a cancelled booking cannot be checked into")
    void cancelledBookingsCannotBeCheckedInto() {
        BookingView held = bookingService.claim(alice, seatOne, TODAY, null);
        bookingService.cancel(held.id(), alice);

        assertThatThrownBy(() -> bookingService.checkIn(held.id(), alice))
                .isInstanceOf(BookingNotActiveException.class);
    }

    @Test
    @DisplayName("acting on a booking id that does not exist is a not-found, not a crash")
    void unknownBookingIdIsRefusedCleanly() {
        assertThatThrownBy(() -> bookingService.cancel(999_999L, alice))
                .isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> bookingService.checkIn(999_999L, alice))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ─── Fixture ─────────────────────────────────────────────────────────────────

    private void moveClockTo(LocalDate day, LocalTime timeOfDay) {
        clock.setTo(ZonedDateTime.of(day, timeOfDay, office));
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
