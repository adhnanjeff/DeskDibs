package com.deskdibs.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findByIdUserId(Long userId);

    List<TeamMember> findByIdTeamId(Long teamId);

    boolean existsByIdTeamIdAndIdUserId(Long teamId, Long userId);
}
