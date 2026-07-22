package com.deskdibs.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** The day snapshot the seat map renders from. */
    List<Booking> findByBookingDateAndStatus(LocalDate bookingDate, BookingStatus status);

    Optional<Booking> findBySeatIdAndBookingDateAndStatus(Long seatId, LocalDate bookingDate, BookingStatus status);

    Optional<Booking> findByUserIdAndBookingDateAndStatus(Long userId, LocalDate bookingDate, BookingStatus status);

    /** Replays the original result of a retried claim instead of failing it as a conflict. */
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByUserIdAndBookingDateGreaterThanEqualOrderByBookingDateAsc(Long userId, LocalDate from);

    long countBySeatIdAndBookingDateAndStatus(Long seatId, LocalDate bookingDate, BookingStatus status);

    long countByUserIdAndBookingDateAndStatus(Long userId, LocalDate bookingDate, BookingStatus status);
}
