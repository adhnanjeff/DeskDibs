package com.deskdibs.team;

import com.deskdibs.booking.Booking;
import com.deskdibs.booking.BookingRepository;
import com.deskdibs.common.OfficeClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservation;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Holding seats for a team, and releasing a hold.
 *
 * <h2>Partial success, never a forced cancellation</h2>
 * Per PLAN.md §4/§7, a manager reserving seats that are already booked gets a report of which seats
 * were held and which were not, naming who holds each on which day. Nothing here ever cancels
 * somebody's booking to make room: a seat with any ACTIVE booking on any day in the requested range
 * is simply left out of the hold and reported as unavailable, and every other requested seat is
 * still held. This is a normal, expected outcome, not a failure — {@code create} throws only for a
 * genuinely malformed request (an unknown team or seat id, or an inverted date range), each of
 * which fails the whole call and leaves nothing held, via the surrounding {@code @Transactional}.
 *
 * <h2>Object-level authorization on both ends</h2>
 * {@code @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")} on the controller proves only that the
 * caller holds one of those roles; it says nothing about <em>which</em> team or <em>which</em> hold
 * they may touch. Both questions are answered here, per object, exactly as
 * {@code BookingService#requireMayAct} does for bookings.
 *
 * <p>{@link #create} requires the caller to manage the team they are holding seats for (or be an
 * admin) — otherwise any manager could take a block of desks in another department's name.
 * {@link #release} requires the hold's creator, the manager of the team it is for, or an admin.
 */
@Service
public class ReservationService {

    private final TeamRepository teams;
    private final SeatRepository seats;
    private final SeatReservationRepository reservations;
    private final BookingRepository bookings;
    private final AppUserRepository users;
    private final OfficeProperties office;
    private final OfficeClock officeClock;

    public ReservationService(TeamRepository teams,
                              SeatRepository seats,
                              SeatReservationRepository reservations,
                              BookingRepository bookings,
                              AppUserRepository users,
                              OfficeProperties office,
                              OfficeClock officeClock) {
        this.teams = teams;
        this.seats = seats;
        this.reservations = reservations;
        this.bookings = bookings;
        this.users = users;
        this.office = office;
        this.officeClock = officeClock;
    }

    /**
     * Hold as many of {@code seatIds} as are free of any ACTIVE booking across
     * {@code [startDate, endDate]}, for {@code teamId}, releasing daily at {@code releaseAtTime}
     * (or the configured default when {@code null}).
     *
     * @throws TeamNotFoundException             no team with that id
     * @throws TeamAccessDeniedException         the caller does not manage that team and is not an admin
     * @throws ReservationSeatNotFoundException  one of the requested seat ids does not exist
     * @throws InvalidReservationRangeException  {@code endDate} is before {@code startDate}
     */
    @Transactional
    public ReservationReport create(long actingUserId, long teamId, List<Long> seatIds, LocalDate startDate,
                                    LocalDate endDate, LocalTime releaseAtTime) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidReservationRangeException(startDate, endDate);
        }

        Team team = teams.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
        AppUser actor = users.findById(actingUserId).orElseThrow();

        // Holding desks is a mutation on somebody else's team, so the role alone is not enough:
        // this manager must be *this* team's manager.
        if (actor.getRole() != UserRole.ADMIN && !managesTeam(team, actingUserId)) {
            throw new TeamAccessDeniedException(teamId, actingUserId);
        }

        LocalTime effectiveReleaseAtTime = releaseAtTime == null ? office.teamBlockReleaseTime() : releaseAtTime;

        List<ReservationReport.HeldSeat> held = new ArrayList<>();
        List<ReservationReport.UnavailableSeat> unavailable = new ArrayList<>();

        for (Long seatId : seatIds) {
            Seat seat = seats.findById(seatId).orElseThrow(() -> new ReservationSeatNotFoundException(seatId));

            List<Booking> conflicts = bookings.findActiveBookingsForSeatInRangeFetchUser(seatId, startDate, endDate);
            if (!conflicts.isEmpty()) {
                Booking firstConflict = conflicts.get(0);
                unavailable.add(new ReservationReport.UnavailableSeat(seatId, seat.getLabel(),
                        firstConflict.getBookingDate(), firstConflict.getUser().getId(),
                        firstConflict.getUser().getDisplayName()));
                continue;
            }

            SeatReservation reservation = new SeatReservation(seat, team, startDate, endDate, actor);
            reservation.setReleaseAtTime(effectiveReleaseAtTime);
            reservations.saveAndFlush(reservation);
            held.add(new ReservationReport.HeldSeat(reservation.getId(), seatId, seat.getLabel()));
        }

        return new ReservationReport(team.getId(), team.getName(), startDate, endDate, held, unavailable);
    }

    // ─── Reading ─────────────────────────────────────────────────────────────────

    /**
     * The teams this caller may hold seats for: every team for an admin, and only the teams they
     * manage for a manager.
     *
     * <p>The same rule {@link #create} enforces, which is the point — the UI offers exactly the
     * choices the API will accept, so a manager is never shown a team that would be refused. The
     * check still lives in {@code create}: this list is a convenience, never the control.
     */
    @Transactional(readOnly = true)
    public List<TeamView> teamsFor(long actingUserId) {
        AppUser actor = users.findById(actingUserId).orElseThrow();
        List<Team> visible = actor.getRole() == UserRole.ADMIN
                ? teams.findAllByOrderByNameAsc()
                : teams.findByManagerIdOrderByNameAsc(actingUserId);
        return visible.stream().map(TeamView::of).toList();
    }

    /**
     * Live and upcoming holds this caller may act on, so the UI can list a block and release it.
     *
     * <p>Filtered by the same three-way test {@link #release} applies per hold — creator, team
     * manager, or admin — so every row returned is one the caller can actually release.
     */
    @Transactional(readOnly = true)
    public List<ReservationView> upcomingFor(long actingUserId, LocalDate from) {
        AppUser actor = users.findById(actingUserId).orElseThrow();
        boolean admin = actor.getRole() == UserRole.ADMIN;

        LocalDate today = officeClock.today();

        return reservations.findNotEndedBeforeFetchAll(from).stream()
                .filter(r -> admin
                        || managesTeam(r.getTeam(), actingUserId)
                        || (r.getCreatedBy() != null && r.getCreatedBy().getId().longValue() == actingUserId))
                .map(r -> ReservationView.of(r, isEnforcedNow(r, today)))
                .toList();
    }

    /**
     * Is this hold holding anything right now?
     *
     * <p>Mirrors {@code SeatMapService#firstEnforcedHold} exactly, so the manager's list and the
     * floor map can never tell different stories about the same hold. A block for a future day is
     * enforced (it has not reached its release time yet); a block for today is enforced only until
     * that time passes; a block whose last day is behind us holds nothing.
     */
    private boolean isEnforcedNow(SeatReservation hold, LocalDate today) {
        if (hold.getEndDate().isBefore(today)) {
            return false;
        }
        LocalDate effectiveDay = hold.getStartDate().isAfter(today) ? hold.getStartDate() : today;
        return officeClock.isBefore(effectiveDay, hold.getReleaseAtTime());
    }

    // ─── Release ─────────────────────────────────────────────────────────────────

    /**
     * Release a hold early. Deleted outright rather than soft-cancelled: unlike a booking,
     * {@code seat_reservation} carries no status and no history requirement — once released, a
     * hold has nothing further to say.
     *
     * @throws ReservationNotFoundException      no reservation with that id
     * @throws ReservationAccessDeniedException  the caller did not create it, does not manage the
     *                                           team it is for, and is not an admin
     */
    @Transactional
    public void release(long reservationId, long actingUserId) {
        SeatReservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        AppUser actor = users.findById(actingUserId).orElseThrow();

        boolean permitted = reservation.getCreatedBy().getId().longValue() == actingUserId
                || actor.getRole() == UserRole.ADMIN
                || managesTeam(reservation.getTeam(), actingUserId);

        if (!permitted) {
            throw new ReservationAccessDeniedException(reservationId, actingUserId);
        }

        reservations.delete(reservation);
    }

    private static boolean managesTeam(Team team, long actingUserId) {
        AppUser manager = team.getManager();
        return manager != null && manager.getId().longValue() == actingUserId;
    }
}
