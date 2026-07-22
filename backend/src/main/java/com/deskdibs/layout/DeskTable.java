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

/**
 * A physical desk table. Named DeskTable because {@code table} is reserved in SQL.
 *
 * <p>{@code posX}/{@code posY}/{@code rotation} are logical layout coordinates that the seat map
 * renders from; seats derive their screen position from their table plus their side and index,
 * so moving a table moves its seats for free.
 */
@Entity
@Table(name = "desk_table")
@Getter
@Setter
@NoArgsConstructor
public class DeskTable extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    /** Always even: seats are split equally between side A and side B. */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "pos_x", nullable = false)
    private int posX;

    @Column(name = "pos_y", nullable = false)
    private int posY;

    @Column(name = "rotation", nullable = false)
    private int rotation;

    public DeskTable(Zone zone, String label, int capacity, int posX, int posY, int rotation) {
        this.zone = zone;
        this.label = label;
        this.capacity = capacity;
        this.posX = posX;
        this.posY = posY;
        this.rotation = rotation;
    }
}
