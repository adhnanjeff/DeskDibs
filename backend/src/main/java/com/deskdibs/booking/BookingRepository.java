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

    /**
     * The day snapshot with its occupant eagerly joined. Used by the seat map, which needs every
     * occupant's display name for up to 110 seats in one round trip rather than one lazy load per
     * booking. {@code seat} is deliberately not joined here: the caller already knows which seat
     * each booking belongs to (it groups these by {@code b.getSeat().getId()}, which reads the
     * foreign key off the entity without initialising it), so joining it again would be wasted work.
     */
    @Query("""
           select b from Booking b
           join fetch b.user
           where b.bookingDate = :bookingDate and b.status = :status
           """)
    List<Booking> findByBookingDateAndStatusFetchUser(@Param("bookingDate") LocalDate bookingDate,
                                                      @Param("status") BookingStatus status);

    /** The caller's own bookings in a date range, with the seat eagerly joined for its label. */
    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.user.id = :userId and b.bookingDate between :from and :to
           order by b.bookingDate asc
           """)
    List<Booking> findByUserIdAndBookingDateBetweenOrderByBookingDateAscFetchSeatAndUser(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Every ACTIVE booking for one seat across a date range, with the occupant eagerly joined —
     * exactly what a team-reservation request needs to report who is already sitting there on which
     * day, without force-cancelling anybody.
     */
    @Query("""
           select b from Booking b
           join fetch b.user
           where b.seat.id = :seatId and b.bookingDate between :from and :to and b.status = 'ACTIVE'
           order by b.bookingDate asc
           """)
    List<Booking> findActiveBookingsForSeatInRangeFetchUser(@Param("seatId") Long seatId,
                                                            @Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);
}
