package com.deskdibs.admin;

import com.deskdibs.booking.BookingView;

import java.time.LocalDate;

/**
 * One booking an administrative action handed back to the pool.
 *
 * <p>Reported rather than merely counted because both actions that produce these are irreversible
 * from the administrator's point of view: they need to see <em>who</em> lost a desk and on which
 * day, not just that four bookings went away.
 */
public record ReleasedBookingView(
        Long bookingId,
        Long seatId,
        String seatLabel,
        LocalDate bookingDate,
        Long userId,
        String userDisplayName) {

    static ReleasedBookingView of(BookingView booking) {
        return new ReleasedBookingView(
                booking.id(),
                booking.seatId(),
                booking.seatLabel(),
                booking.bookingDate(),
                booking.userId(),
                booking.userDisplayName());
    }
}
