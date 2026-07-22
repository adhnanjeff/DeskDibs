package com.deskdibs.team;

import com.deskdibs.common.BaseEntity;
import com.deskdibs.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * A group of people who want to sit together. Managed in the app rather than imported from the
 * org chart, because org-chart groups rarely match who actually needs to sit together.
 */
@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
public class Team extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Nullable so a team can exist between managers. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id")
    private AppUser manager;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Team(String name, AppUser manager) {
        this.name = name;
        this.manager = manager;
    }
}
