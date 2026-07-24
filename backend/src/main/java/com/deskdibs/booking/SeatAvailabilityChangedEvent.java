package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * One seat's availability changed on one date — published by {@code BookingService} the instant a
 * claim, move, cancel or check-in succeeds, and turned into a {@code SeatStatusChanged} broadcast by
 * {@code com.deskdibs.realtime.SeatMapBroadcastListener} once the transaction that raised it
 * commits.
 *
 * <p>Deliberately carries only the two facts needed to look the seat back up, not a pre-built
 * broadcast payload. The listener re-queries the seat's state after commit rather than trusting
 * whatever this event's publisher computed before the transaction finished, which is what makes
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} the correct tool here: a plain
 * {@code ApplicationListener} would run synchronously inside the still-open transaction and could
 * observe (or broadcast) a write that goes on to roll back.
 *
 * <p>A single {@code move} publishes two of these, one for the seat left behind and one for the
 * seat claimed, because both seats' availability changed even though only one HTTP request
 * happened.
 *
 * <p>{@code public} so {@code com.deskdibs.realtime.SeatMapBroadcastListener} can declare a listener
 * method with this type as its parameter. That is the only dependency in play: {@code booking}
 * publishes it through the generic {@code ApplicationEventPublisher} and never references the
 * {@code realtime} package, so the edge runs one way.
 */
public record SeatAvailabilityChangedEvent(long seatId, LocalDate bookingDate) {
}
