package com.deskdibs.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    Optional<Seat> findByLabel(String label);

    List<Seat> findByDeskTableId(Long deskTableId);

    List<Seat> findByDeskTableZoneId(Long zoneId);

    long countByDeskTableZoneId(Long zoneId);

    long countByStatus(SeatStatus status);

    /**
     * Every seat with its table, zone and floor eagerly joined, in stable render order. This is
     * what makes {@code GET /api/seatmap} one round trip: without the fetch joins, walking
     * {@code seat.getDeskTable().getZone().getFloor()} for each of 110 seats would be an N+1 read of
     * the layout on top of whatever this query itself costs.
     */
    @Query("""
           select s from Seat s
           join fetch s.deskTable t
           join fetch t.zone z
           join fetch z.floor f
           order by f.displayOrder, z.displayOrder, t.posY, t.posX, s.side, s.seatIndex
           """)
    List<Seat> findAllWithLayoutOrdered();
}
