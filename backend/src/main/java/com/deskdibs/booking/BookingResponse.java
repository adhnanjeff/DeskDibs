package com.deskdibs.booking;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The wire shape of a booking, mapped from {@link BookingView} at the controller boundary.
 *
 * <p>A dedicated response type rather than returning {@link BookingView} straight through, even
 * though the two happen to carry the same fields today: this one is the HTTP contract, annotated
 * and versioned as such, while {@link BookingView} is the service layer's internal return type,
 * free to change for reasons that have nothing to do with the wire.
 */
public record BookingResponse(
        long id,
        long seatId,
        String seatLabel,
        long userId,
        String userDisplayName,
        LocalDate bookingDate,
        BookingStatus status,
        OffsetDateTime checkedInAt,
        String idempotencyKey) {

    public static BookingResponse of(BookingView view) {
        return new BookingResponse(view.id(), view.seatId(), view.seatLabel(), view.userId(),
                view.userDisplayName(), view.bookingDate(), view.status(), view.checkedInAt(),
                view.idempotencyKey());
    }
}
