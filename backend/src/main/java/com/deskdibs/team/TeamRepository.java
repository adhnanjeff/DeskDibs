package com.deskdibs.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByName(String name);

    /** The teams one manager is responsible for — the only teams they may hold seats for. */
    List<Team> findByManagerIdOrderByNameAsc(Long managerId);

    List<Team> findAllByOrderByNameAsc();
}
