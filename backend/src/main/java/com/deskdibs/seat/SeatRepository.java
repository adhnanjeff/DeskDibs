package com.deskdibs.seat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    Optional<Seat> findByLabel(String label);

    List<Seat> findByDeskTableId(Long deskTableId);

    List<Seat> findByDeskTableZoneId(Long zoneId);

    long countByDeskTableZoneId(Long zoneId);

    long countByStatus(SeatStatus status);
}
