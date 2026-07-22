package com.deskdibs.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {

    Optional<Floor> findByName(String name);

    List<Floor> findAllByOrderByDisplayOrderAsc();
}
