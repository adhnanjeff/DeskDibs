package com.deskdibs.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeskTableRepository extends JpaRepository<DeskTable, Long> {

    Optional<DeskTable> findByLabel(String label);

    List<DeskTable> findByZoneId(Long zoneId);

    long countByZoneId(Long zoneId);
}
