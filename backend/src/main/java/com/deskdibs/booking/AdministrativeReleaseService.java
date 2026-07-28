package com.deskdibs.booking;

import com.deskdibs.common.OfficeClock;
import com.deskdibs.common.OfficeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hands desks back when something happens <em>to</em> a booking rather than to it being given up:
 * the person left the company (PLAN.md §5 #12), or the desk left the floor plan (§5 #13).
 *
 * <h2>Why this lives in {@code booking} and not in {@code admin}</h2>
 * Only this package may touch the {@link Booking} entity. The {@code admin} feature orchestrates —
 * it decides that a user is being deactivated or a seat withdrawn — and calls in here for the part
 * that is a booking concern, receiving {@link BookingView} back. That keeps the entity behind its
 * own package boundary and keeps {@code admin} a coordinator rather than a second booking engine.
 *
 * <h2>Same shape as the no-show release, for the same reasons</h2>
 * Rows are read first so the freed seats can be named and broadcast, then a bulk update
 * re-evaluates the predicate at the database. Anything that stopped being ACTIVE between the two
 * statements falls out of the update and is left alone. Both methods are idempotent: running one
 * twice releases nothing the second time.
 *
 * <p>Nothing is deleted. A released row stays as history with a status that says why, which is what
 * lets the affected person read <em>"the desk was withdrawn"</em> rather than finding their booking
 * silently gone.
 */
@Service
public class AdministrativeReleaseService {

    private static final Logger log = LoggerFactory.getLogger(AdministrativeReleaseService.class);

    private final BookingRepository bookings;
    private final OfficeClock officeClock;
    private final OfficeProperties office;
    private final ApplicationEventPublisher events;

    public AdministrativeReleaseService(BookingRepository bookings,
                                        OfficeClock officeClock,
                                        OfficeProperties office,
                                        ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.officeClock = officeClock;
        this.office = office;
        this.events = events;
    }

    /**
     * Release everything {@code userId} still holds from today onward, because their account has
     * been deactivated.
     *
     * <p>From <em>today</em>, not tomorrow: somebody who has left is not coming in this afternoon
     * either, and leaving today's desk held would keep a seat out of the pool on the one day people
     * are actually looking for it.
     *
     * @return the bookings that were released, as they were before the release
     */
    @Transactional
    public List<BookingView> releaseEverythingHeldBy(long userId) {
        LocalDate from = officeClock.today();
        List<Booking> held = bookings.findActiveFromDateForUserFetchSeat(userId, from);
        if (held.isEmpty()) {
            return List.of();
        }

        // Mapped before the update: `clearAutomatically` detaches these, and a view built afterwards
        // would be reporting the post-release status rather than what was actually taken away.
        List<BookingView> released = held.stream().map(BookingView::of).toList();

        int count = bookings.releaseFutureBookingsOfDeactivatedUser(userId, from, officeClock.timestamp());
        released.forEach(booking -> publish(booking.seatId(), booking.bookingDate()));

        log.info("Deactivation of user {}: {} booking(s) returned to the pool from {}", userId, count, from);
        return released;
    }

    /**
     * Release every booking on {@code seatId} from today onward, because the desk has been taken out
     * of the bookable pool.
     *
     * <p>Broadcasts across the whole booking horizon, not only the dates that had a booking: a
     * withdrawn desk renders as unavailable on <em>every</em> day, so a map open on a date nobody had
     * booked would otherwise keep offering a seat that no longer exists until it happened to refetch.
     *
     * @return the bookings that were released, as they were before the release
     */
    @Transactional
    public List<BookingView> releaseEverythingOn(long seatId) {
        LocalDate from = officeClock.today();
        List<Booking> affected = bookings.findActiveFromDateForSeatFetchUser(seatId, from);

        List<BookingView> released = affected.stream().map(BookingView::of).toList();
        if (!released.isEmpty()) {
            int count = bookings.releaseBookingsOnWithdrawnSeat(seatId, from, officeClock.timestamp());
            log.info("Seat {} withdrawn: {} booking(s) returned to the pool from {}", seatId, count, from);
        }

        announceAcrossTheHorizon(seatId, from, released);
        return released;
    }

    /**
     * Repaint one seat on every open map, whatever changed about it. Used when a seat's own status
     * changes — including back to ACTIVE, which releases nothing but still alters every day's map.
     */
    @Transactional
    public void announceSeatAcrossTheHorizon(long seatId) {
        announceAcrossTheHorizon(seatId, officeClock.today(), List.of());
    }

    private void announceAcrossTheHorizon(long seatId, LocalDate from, List<BookingView> released) {
        // A LinkedHashSet because a released booking's date is normally inside the horizon already;
        // announcing the same seat and date twice would send every client a duplicate frame.
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (LocalDate day = from; !day.isAfter(from.plusDays(office.bookingHorizonDays())); day = day.plusDays(1)) {
            dates.add(day);
        }
        // Defensive: a booking made before the horizon was shortened would sit outside the loop
        // above, and its seat still has to be repainted for whoever is looking at that day.
        released.forEach(booking -> dates.add(booking.bookingDate()));

        List<LocalDate> ordered = new ArrayList<>(dates);
        ordered.forEach(date -> publish(seatId, date));
    }

    private void publish(long seatId, LocalDate date) {
        events.publishEvent(new SeatAvailabilityChangedEvent(seatId, date));
    }
}
