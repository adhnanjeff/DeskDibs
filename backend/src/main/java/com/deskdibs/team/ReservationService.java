package com.deskdibs.team;

import com.deskdibs.booking.Booking;
import com.deskdibs.booking.BookingRepository;
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
 * <h2>Object-level authorization on release</h2>
 * {@code @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")} on the controller proves only that the
 * caller holds one of those roles; it says nothing about whether they may touch <em>this</em> hold.
 * {@link #release} answers that per object, exactly as {@code BookingService#requireMayAct} does for
 * bookings: the hold's creator, the manager of the team it is for, or an admin.
 */
@Service
public class ReservationService {

    private final TeamRepository teams;
    private final SeatRepository seats;
    private final SeatReservationRepository reservations;
    private final BookingRepository bookings;
    private final AppUserRepository users;
    private final OfficeProperties office;

    public ReservationService(TeamRepository teams,
                              SeatRepository seats,
                              SeatReservationRepository reservations,
                              BookingRepository bookings,
                              AppUserRepository users,
                              OfficeProperties office) {
        this.teams = teams;
        this.seats = seats;
        this.reservations = reservations;
        this.bookings = bookings;
        this.users = users;
        this.office = office;
    }

    /**
     * Hold as many of {@code seatIds} as are free of any ACTIVE booking across
     * {@code [startDate, endDate]}, for {@code teamId}, releasing daily at {@code releaseAtTime}
     * (or the configured default when {@code null}).
     *
     * @throws TeamNotFoundException             no team with that id
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
        AppUser actor = users.getReferenceById(actingUserId);
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
