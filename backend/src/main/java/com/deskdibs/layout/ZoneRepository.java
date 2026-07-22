package com.deskdibs.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    Optional<Zone> findByFloorIdAndName(Long floorId, String name);

    List<Zone> findByFloorIdOrderByDisplayOrderAsc(Long floorId);
}
