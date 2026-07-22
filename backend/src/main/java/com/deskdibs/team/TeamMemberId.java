package com.deskdibs.team;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link TeamMember}. Value object: equality is by both columns. */
@Embeddable
@Getter
@NoArgsConstructor
public class TeamMemberId implements Serializable {

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public TeamMemberId(Long teamId, Long userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TeamMemberId that)) {
            return false;
        }
        return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, userId);
    }

    @Override
    public String toString() {
        return "TeamMemberId[teamId=" + teamId + ", userId=" + userId + "]";
    }
}
