package com.deskdibs.booking;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * What the booking engine hands back: a flat, detached snapshot of one booking.
 *
 * <p>The service returns this rather than the {@link Booking} entity. With
 * {@code open-in-view: false} a caller holding an entity would hit a lazy {@code seat} or
 * {@code user} proxy the moment the transaction closed; flattening the two fields anyone actually
 * needs — the seat label and the occupant's display name — removes that trap and keeps the entity
 * inside the persistence layer where it belongs.
 *
 * @param seatLabel       human-facing label, e.g. {@code R3-A2}
 * @param userDisplayName occupant's display name, for the hover card and for the losing claimant's
 *                        <em>"Alice grabbed that one"</em>
 * @param checkedInAt     {@code null} until the occupant checks in
 * @param idempotencyKey  the key this booking was created under, {@code null} if none was supplied
 */
public record BookingView(
        Long id,
        Long seatId,
        String seatLabel,
        Long userId,
        String userDisplayName,
        LocalDate bookingDate,
        BookingStatus status,
        OffsetDateTime checkedInAt,
        String idempotencyKey) {

    static BookingView of(Booking booking) {
        return new BookingView(
                booking.getId(),
                booking.getSeat().getId(),
                booking.getSeat().getLabel(),
                booking.getUser().getId(),
                booking.getUser().getDisplayName(),
                booking.getBookingDate(),
                booking.getStatus(),
                booking.getCheckedInAt(),
                booking.getIdempotencyKey());
    }

    public boolean isActive() {
        return status == BookingStatus.ACTIVE;
    }
}
