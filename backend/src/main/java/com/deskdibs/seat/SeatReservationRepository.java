package com.deskdibs.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    /** Holds covering a given day — {@code startDate <= date <= endDate}. */
    List<SeatReservation> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate onOrBefore,
                                                                                LocalDate onOrAfter);

    List<SeatReservation> findBySeatIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(Long seatId,
                                                                                          LocalDate onOrBefore,
                                                                                          LocalDate onOrAfter);

    List<SeatReservation> findByTeamId(Long teamId);

    /**
     * Every hold covering a given day, with its team eagerly joined. Used by the seat map, which
     * needs every hold's team name for up to 110 seats in one round trip rather than one lazy load
     * per hold.
     */
    @Query("""
           select r from SeatReservation r
           join fetch r.team
           where r.startDate <= :date and r.endDate >= :date
           """)
    List<SeatReservation> findActiveOnDateFetchTeam(@Param("date") LocalDate date);

    /**
     * Every hold overlapping a date range, as {@code [seatId, startDate, endDate, releaseAtTime]}
     * rows, for the date strip's per-day held count.
     *
     * <p>Projected rather than returning entities, and expanded across days by the caller: a hold is
     * one row covering a span, so there is no way to group it per day in JPQL, and the strip needs a
     * number for each day of the horizon. The row count is the number of blocks a manager has
     * created, not seats × days, so the expansion is cheap.
     *
     * <p>Seats out of service are excluded because they are not in the denominator either — the
     * strip's {@code bookableSeats} already leaves them out, so counting a hold on one would take
     * the same desk away twice.
     */
    @Query("""
           select r.seat.id, r.startDate, r.endDate, r.releaseAtTime
             from SeatReservation r
            where r.startDate <= :to and r.endDate >= :from
              and r.seat.status = com.deskdibs.seat.SeatStatus.ACTIVE
           """)
    List<Object[]> findOverlappingRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Holds that have not finished yet, with everything the manager UI shows eagerly joined.
     * Expired holds are left out: a block that ended last week is history nobody can act on, and
     * {@code seat_reservation} keeps no status to distinguish it by.
     */
    @Query("""
           select r from SeatReservation r
           join fetch r.seat
           join fetch r.team
           left join fetch r.createdBy
           where r.endDate >= :from
           order by r.startDate asc, r.seat.label asc
           """)
    List<SeatReservation> findNotEndedBeforeFetchAll(@Param("from") LocalDate from);
}
