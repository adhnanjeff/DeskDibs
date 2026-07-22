package com.deskdibs.layout;

import com.deskdibs.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A named area of a floor — "Left Wing" / "Right Wing" in the interim layout. */
@Entity
@Table(name = "zone")
@Getter
@Setter
@NoArgsConstructor
public class Zone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Zone(Floor floor, String name, int displayOrder) {
        this.floor = floor;
        this.name = name;
        this.displayOrder = displayOrder;
    }
}
