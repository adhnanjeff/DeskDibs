package com.deskdibs.admin;

import com.deskdibs.booking.AdministrativeReleaseService;
import com.deskdibs.booking.BookingView;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Changing the floor plan while people are already booked onto it (PLAN.md §5 #13).
 *
 * <h2>Soft-delete, never delete</h2>
 * A desk leaves the pool by taking a {@link SeatStatus} of {@code DISABLED} or {@code BROKEN}, never
 * by being removed from the {@code seat} table. The booking table's foreign key is deliberately
 * {@code ON DELETE RESTRICT} for exactly this reason: somebody's booking history must not vanish
 * because a desk was moved during a refit. A withdrawn seat still renders on the map — greyed, with
 * its own icon — so the floor stays recognisable rather than developing holes.
 *
 * <h2>How the affected people are told</h2>
 * Their booking is flipped to {@code RELEASED_SEAT_REMOVED} rather than cancelled, and that status
 * travels all the way to the client. The desk they lost, the day they lost it, and the reason are
 * therefore all visible on their own bookings page the next time they look, without this system
 * needing an email or Teams channel it does not have. That is the honest reading of
 * <em>"users notified"</em> for an application with no messaging infrastructure; if a push channel
 * is added later it should hang off the same released list this returns, not replace it.
 */
@Service
public class SeatAdminService {

    private final SeatRepository seats;
    private final AdministrativeReleaseService releases;

    public SeatAdminService(SeatRepository seats, AdministrativeReleaseService releases) {
        this.seats = seats;
        this.releases = releases;
    }

    /**
     * Move a desk into or out of the bookable pool.
     *
     * <p>Taking it out releases every booking on it from today onward. Putting it back releases
     * nothing — it simply becomes claimable again — but still repaints every open map, because a
     * seat that has just returned should be visible to whoever is looking for one right now.
     *
     * @throws AdminSeatNotFoundException no seat with that id
     */
    @Transactional
    public SeatStatusChangeReport setStatus(long seatId, SeatStatus status) {
        Seat seat = seats.findById(seatId).orElseThrow(() -> new AdminSeatNotFoundException(seatId));
        SeatStatus previous = seat.getStatus();

        if (previous == status) {
            return new SeatStatusChangeReport(seat.getId(), seat.getLabel(), previous, status,
                    true, 0, List.of());
        }

        seat.setStatus(status);
        seats.saveAndFlush(seat);

        List<BookingView> released;
        if (status == SeatStatus.ACTIVE) {
            released = List.of();
            releases.announceSeatAcrossTheHorizon(seatId);
        } else {
            released = releases.releaseEverythingOn(seatId);
        }

        return new SeatStatusChangeReport(seat.getId(), seat.getLabel(), previous, status, false,
                released.size(), released.stream().map(ReleasedBookingView::of).toList());
    }
}
