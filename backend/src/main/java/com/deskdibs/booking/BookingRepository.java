package com.deskdibs.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ─── Fetch-joined lookups ────────────────────────────────────────────────────
    //
    // `seat` and `user` are LAZY and `open-in-view` is false, so a booking read without them is
    // only usable while its transaction is open. The service maps some bookings *after* a
    // transaction has rolled back — naming the winner of a lost race, naming the seat you already
    // hold that day — which is exactly where a lazy proxy would blow up. These variants fetch both
    // associations in the same query, so the result can be mapped anywhere.

    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.id = :bookingId
           """)
    Optional<Booking> findByIdWithSeatAndUser(@Param("bookingId") Long bookingId);

    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.seat.id = :seatId and b.bookingDate = :bookingDate and b.status = :status
           """)
    Optional<Booking> findBySeatAndDateWithSeatAndUser(@Param("seatId") Long seatId,
                                                       @Param("bookingDate") LocalDate bookingDate,
                                                       @Param("status") BookingStatus status);

    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.user.id = :userId and b.bookingDate = :bookingDate and b.status = :status
           """)
    Optional<Booking> findByUserAndDateWithSeatAndUser(@Param("userId") Long userId,
                                                       @Param("bookingDate") LocalDate bookingDate,
                                                       @Param("status") BookingStatus status);

    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.idempotencyKey = :idempotencyKey
           """)
    Optional<Booking> findByIdempotencyKeyWithSeatAndUser(@Param("idempotencyKey") String idempotencyKey);
}
