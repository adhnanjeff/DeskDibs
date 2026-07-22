package com.deskdibs.seat;

import com.deskdibs.common.BaseEntity;
import com.deskdibs.team.Team;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * A soft hold placed by a manager so a team can sit together.
 *
 * <p>Soft on purpose: past {@code releaseAtTime} the claim check simply stops enforcing the hold.
 * There is no scheduled job and no state transition, so there is nothing to go wrong — and a
 * reserved-but-empty seat never wastes scarce inventory for a whole day.
 */
@Entity
@Table(name = "seat_reservation")
@Getter
@Setter
@NoArgsConstructor
public class SeatReservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Inclusive, and never before {@code startDate} (enforced by a check constraint). */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "release_at_time", nullable = false)
    private LocalTime releaseAtTime = LocalTime.of(10, 0);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public SeatReservation(Seat seat, Team team, LocalDate startDate, LocalDate endDate, AppUser createdBy) {
        this.seat = seat;
        this.team = team;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
    }
}
