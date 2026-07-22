package com.deskdibs.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.Hibernate;

/**
 * Identifier and identity semantics shared by every single-column-keyed entity.
 *
 * <p>Deliberately hand-written rather than Lombok {@code @Data}/{@code @EqualsAndHashCode}:
 * those generate field-by-field equality, which initialises lazy associations, breaks against
 * Hibernate proxies, and changes an entity's hash code the moment the database assigns its id.
 *
 * <p>The contract implemented here:
 * <ul>
 *   <li>a transient entity (null id) is equal only to itself, so a {@code Set} can hold several
 *       unsaved instances;</li>
 *   <li>a proxy and its initialised target compare equal, because the persistent class is
 *       compared via {@link Hibernate#getClass} rather than {@link Object#getClass()};</li>
 *   <li>{@code hashCode} is stable across the transient to persistent transition, so an entity
 *       stays findable in a hash-based collection after it is saved.</li>
 * </ul>
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        // Hibernate.getClass unwraps a proxy without initialising it.
        if (!Hibernate.getClass(this).equals(Hibernate.getClass(other))) {
            return false;
        }
        Long thisId = this.getId();
        return thisId != null && thisId.equals(that.getId());
    }

    @Override
    public final int hashCode() {
        // Constant per persistent class: the id is not stable across a save, but the type is.
        return Hibernate.getClass(this).hashCode();
    }
}
