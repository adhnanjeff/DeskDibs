package com.deskdibs.realtime;

import com.deskdibs.booking.SeatAvailabilityChangedEvent;
import com.deskdibs.booking.SeatMapService;
import com.deskdibs.seat.SeatMapView;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a committed {@link SeatAvailabilityChangedEvent} into a {@link SeatStatusChanged} broadcast
 * on {@code /topic/seatmap/{date}}.
 *
 * <h2>Why {@code AFTER_COMMIT} and not the default phase</h2>
 * {@code BookingService} publishes its event from inside the very transaction that performs the
 * write, before that transaction has committed or rolled back — see that event's javadoc.
 * {@code phase = AFTER_COMMIT} defers this method until Spring's transaction synchronization
 * confirms the commit actually happened, and, critically, never invokes it at all if the
 * transaction rolls back instead. Broadcasting inside the transaction would tell every open map a
 * seat changed and then roll back, leaving every client wrong until it happened to refresh; this is
 * the one line that rules that out. A claim that loses the seat race never reaches the line that
 * publishes the event in the first place, so a rolled-back claim broadcasts nothing either way.
 */
@Component
public class SeatMapBroadcastListener {

    private static final String TOPIC_PREFIX = "/topic/seatmap/";

    private final SeatMapService seatMapService;
    private final SimpMessagingTemplate messagingTemplate;

    public SeatMapBroadcastListener(SeatMapService seatMapService, SimpMessagingTemplate messagingTemplate) {
        this.seatMapService = seatMapService;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatAvailabilityChanged(SeatAvailabilityChangedEvent event) {
        // Re-queried after commit, deliberately: this reflects what is now actually true rather
        // than whatever the publisher computed a moment before its transaction finished.
        SeatMapView seat = seatMapService.snapshotOf(event.seatId(), event.bookingDate());
        messagingTemplate.convertAndSend(TOPIC_PREFIX + event.bookingDate(),
                new SeatStatusChanged(event.bookingDate(), seat));
    }
}
