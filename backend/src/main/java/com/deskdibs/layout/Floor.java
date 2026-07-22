package com.deskdibs.layout;

import com.deskdibs.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A floor of the office. Multi-floor support costs nothing now and saves a migration later,
 * even though the interim layout has exactly one.
 */
@Entity
@Table(name = "floor")
@Getter
@Setter
@NoArgsConstructor
public class Floor extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Floor(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }
}
