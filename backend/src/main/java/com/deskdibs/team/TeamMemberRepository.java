package com.deskdibs.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findByIdUserId(Long userId);

    List<TeamMember> findByIdTeamId(Long teamId);

    boolean existsByIdTeamIdAndIdUserId(Long teamId, Long userId);

    /**
     * Does {@code managerUserId} manage any team that {@code memberUserId} belongs to?
     *
     * <p>This is the object-level authorization question behind "a manager may cancel a booking
     * held by someone on their team". Written as an explicit query rather than a derived one
     * because the path crosses two associations ({@code team.manager.id}), and an authorization
     * rule should not depend on method-name parsing.
     */
    @Query("""
           select count(m) > 0 from TeamMember m
           where m.id.userId = :memberUserId and m.team.manager.id = :managerUserId
           """)
    boolean existsMembershipManagedBy(@Param("memberUserId") Long memberUserId,
                                      @Param("managerUserId") Long managerUserId);
}
