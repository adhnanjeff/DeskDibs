package com.deskdibs.booking;

import com.deskdibs.common.OfficeClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Puts no-shows back in the pool.
 *
 * <p>A booking that nobody checked into by the cut-off stops holding its seat: the row flips to
 * {@link BookingStatus#RELEASED_NO_SHOW}, which drops it out of the partial unique indexes and
 * makes the seat immediately claimable by anyone. Nothing is deleted — the row stays as history,
 * exactly like a cancellation.
 *
 * <p>Split from the scheduler on purpose. The trigger is a line of cron; the behaviour is this
 * class, and it takes the date as a parameter so a test can release a specific day against a
 * movable clock instead of waiting for 11:00 to come round.
 *
 * <p><b>Idempotent.</b> Running it twice releases nothing the second time, because the second run's
 * predicate no longer matches rows that are already released. That is what makes it safe to retry
 * after a failure, and safe if two application instances ever fire it at once.
 */
@Service
public class NoShowReleaseService {

    private static final Logger log = LoggerFactory.getLogger(NoShowReleaseService.class);

    private final BookingRepository bookings;
    private final OfficeClock officeClock;
    private final ApplicationEventPublisher events;

    public NoShowReleaseService(BookingRepository bookings,
                                OfficeClock officeClock,
                                ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.officeClock = officeClock;
        this.events = events;
    }

    /** Release today's no-shows, where "today" is the office's, never the server's or a client's. */
    @Transactional
    public int releaseTodaysNoShows() {
        return releaseNoShows(officeClock.today());
    }

    /**
     * Release every un-checked-in booking held on {@code date}.
     *
     * <p>Candidates are read before the update so the freed seats can be announced, then the update
     * itself re-checks the predicate at the database. A seat whose owner checked in between those
     * two statements keeps its booking and is broadcast anyway — harmless, because the broadcast
     * listener re-queries the seat's real state after commit rather than trusting a payload
     * computed here (see {@link SeatAvailabilityChangedEvent}).
     *
     * @return how many bookings were released
     */
    @Transactional
    public int releaseNoShows(LocalDate date) {
        List<Booking> candidates = bookings.findNoShowCandidates(date);
        if (candidates.isEmpty()) {
            return 0;
        }

        List<Long> seatIds = candidates.stream().map(b -> b.getSeat().getId()).toList();
        int released = bookings.releaseNoShows(date, officeClock.timestamp());

        for (Long seatId : seatIds) {
            events.publishEvent(new SeatAvailabilityChangedEvent(seatId, date));
        }

        log.info("No-show release for {}: {} booking(s) returned to the pool", date, released);
        return released;
    }
}
