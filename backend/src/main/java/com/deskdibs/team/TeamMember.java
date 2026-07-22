package com.deskdibs.team;

import com.deskdibs.user.AppUser;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Membership link. A person may belong to several teams, so this is a genuine many-to-many
 * with a composite primary key rather than a column on {@code app_user}.
 *
 * <p>Does not extend {@code BaseEntity}: its identity is the composite key, not a generated id.
 */
@Entity
@Table(name = "team_member")
@Getter
@Setter
@NoArgsConstructor
public class TeamMember {

    @EmbeddedId
    private TeamMemberId id;

    @MapsId("teamId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    public TeamMember(Team team, AppUser user) {
        this.team = team;
        this.user = user;
        this.id = new TeamMemberId(team.getId(), user.getId());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TeamMember that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
