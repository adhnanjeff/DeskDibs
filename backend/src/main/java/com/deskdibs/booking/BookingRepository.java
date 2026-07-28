package com.deskdibs.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    // ─── Booking horizon (the date strip) ────────────────────────────────────────

    /**
     * How many desks are claimed on each day in a range, as {@code [date, count]} rows.
     *
     * <p>One grouped query for the whole 14-day horizon rather than fourteen seat-map builds. The
     * strip only needs a number per day; building the full floor for each of them to count it would
     * be roughly a hundred times the work for the same answer.
     */
    @Query("""
           select b.bookingDate, count(b)
             from Booking b
            where b.bookingDate between :from and :to and b.status = 'ACTIVE'
            group by b.bookingDate
           """)
    List<Object[]> countActiveByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The caller's own live bookings across the horizon, so the strip can mark their days. */
    @Query("""
           select b from Booking b
           join fetch b.seat
            where b.user.id = :userId and b.bookingDate between :from and :to and b.status = 'ACTIVE'
           """)
    List<Booking> findMyActiveBookingsBetweenFetchSeat(@Param("userId") Long userId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);

    // ─── No-show release ─────────────────────────────────────────────────────────

    /**
     * Who held a seat on {@code date} and never turned up. Read before the release so the seats
     * freed can be broadcast; {@code seat} is deliberately not fetch-joined, because the caller
     * only needs each booking's seat id, which reads off the foreign key without initialising the
     * proxy.
     */
    @Query("""
           select b from Booking b
           where b.bookingDate = :date and b.status = 'ACTIVE' and b.checkedInAt is null
           """)
    List<Booking> findNoShowCandidates(@Param("date") LocalDate date);

    /**
     * Release every no-show on {@code date} in one statement.
     *
     * <p>A bulk update rather than a loop over entities, because the {@code where} clause is
     * re-evaluated by the database at write time: somebody checking in during the same second the
     * job runs simply falls out of the predicate and keeps their seat. Reading the rows first and
     * then saving them one by one would decide their fate from a snapshot taken before that
     * check-in landed.
     *
     * <p>{@code updatedAt} is set explicitly because a bulk update bypasses Hibernate's
     * {@code @UpdateTimestamp}, and {@code clearAutomatically} drops the now-stale entities from
     * the persistence context so nothing downstream reads a pre-release copy.
     *
     * @return how many bookings actually moved to {@code RELEASED_NO_SHOW}
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Booking b
              set b.status = com.deskdibs.booking.BookingStatus.RELEASED_NO_SHOW, b.updatedAt = :now
            where b.bookingDate = :date and b.status = 'ACTIVE' and b.checkedInAt is null
           """)
    int releaseNoShows(@Param("date") LocalDate date, @Param("now") OffsetDateTime now);

    // ─── Administrative releases (PLAN.md §5 #12 and #13) ────────────────────────
    //
    // Both follow the same shape as the no-show release above and for the same reason: the rows are
    // read first so the freed seats can be broadcast, then a bulk update re-evaluates the predicate
    // at the database. Anything that stopped being ACTIVE between the two statements simply falls
    // out and is left alone.
    //
    // "From today onward" rather than "strictly after today" in both cases. A desk taken out of
    // service this morning cannot be sat at this afternoon, and somebody who has left the company
    // is not coming in today either.

    /**
     * Everything one person still holds from {@code from} onward, with the seat joined so the
     * caller can name each desk it is about to hand back.
     */
    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.user.id = :userId and b.bookingDate >= :from and b.status = 'ACTIVE'
           order by b.bookingDate asc
           """)
    List<Booking> findActiveFromDateForUserFetchSeat(@Param("userId") Long userId,
                                                     @Param("from") LocalDate from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Booking b
              set b.status = com.deskdibs.booking.BookingStatus.RELEASED_USER_DEACTIVATED,
                  b.updatedAt = :now
            where b.user.id = :userId and b.bookingDate >= :from and b.status = 'ACTIVE'
           """)
    int releaseFutureBookingsOfDeactivatedUser(@Param("userId") Long userId,
                                               @Param("from") LocalDate from,
                                               @Param("now") OffsetDateTime now);

    /**
     * Everything still booked on one seat from {@code from} onward, with the occupant joined so the
     * caller can report who is affected by withdrawing the desk.
     */
    @Query("""
           select b from Booking b
           join fetch b.seat
           join fetch b.user
           where b.seat.id = :seatId and b.bookingDate >= :from and b.status = 'ACTIVE'
           order by b.bookingDate asc
           """)
    List<Booking> findActiveFromDateForSeatFetchUser(@Param("seatId") Long seatId,
                                                     @Param("from") LocalDate from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Booking b
              set b.status = com.deskdibs.booking.BookingStatus.RELEASED_SEAT_REMOVED,
                  b.updatedAt = :now
            where b.seat.id = :seatId and b.bookingDate >= :from and b.status = 'ACTIVE'
           """)
    int releaseBookingsOnWithdrawnSeat(@Param("seatId") Long seatId,
                                       @Param("from") LocalDate from,
                                       @Param("now") OffsetDateTime now);
}
