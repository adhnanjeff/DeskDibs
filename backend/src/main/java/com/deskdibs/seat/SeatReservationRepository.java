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
}
