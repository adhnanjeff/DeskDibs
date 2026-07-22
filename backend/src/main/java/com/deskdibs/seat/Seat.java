package com.deskdibs.seat;

import com.deskdibs.common.BaseEntity;
import com.deskdibs.layout.DeskTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One bookable seat.
 *
 * <p>Note what is deliberately absent: no occupancy column, no {@code @Version}. Whether a seat is
 * taken on a date is derived from {@code booking}, whose partial unique index is the single
 * authoritative guard. A copy of that state here would be a second, weaker version of the rule.
 */
@Entity
@Table(name = "seat")
@Getter
@Setter
@NoArgsConstructor
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private DeskTable deskTable;

    /** {@code {table}-{side}{index}}, e.g. {@code R3-A2}. */
    @Column(name = "label", nullable = false, length = 40)
    private String label;

    // Hibernate would map a length-1 string to char(1); the column is varchar(1), so say so.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "side", nullable = false, length = 1)
    private SeatSide side;

    /** 1-based position along the side. */
    @Column(name = "seat_index", nullable = false)
    private int seatIndex;

    @Column(name = "accessible", nullable = false)
    private boolean accessible;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status = SeatStatus.ACTIVE;

    public Seat(DeskTable deskTable, String label, SeatSide side, int seatIndex) {
        this.deskTable = deskTable;
        this.label = label;
        this.side = side;
        this.seatIndex = seatIndex;
    }

    public boolean isBookable() {
        return status == SeatStatus.ACTIVE;
    }
}
