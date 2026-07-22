package com.deskdibs.seat;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
