package com.deskdibs.booking;

import com.deskdibs.common.BaseEntity;
import com.deskdibs.seat.Seat;
import com.deskdibs.user.AppUser;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One person's claim on one seat for one whole day.
 *
 * <p>The uniqueness rules that make double-booking impossible are <em>not</em> declared here.
 * They live in {@code V1__core_schema.sql} as partial unique indexes:
 *
 * <pre>
 *   uq_seat_active_per_date  ON booking (seat_id, booking_date) WHERE status = 'ACTIVE'
 *   uq_user_active_per_date  ON booking (user_id, booking_date) WHERE status = 'ACTIVE'
 * </pre>
 *
 * JPA cannot express a partial index, and expressing it here would be a weaker duplicate anyway:
 * the guarantee has to sit below the application so that no bug, race, or retry can bypass it.
 * A losing insert must be allowed to fail on the constraint and surface as a 409 — never
 * pre-empted with a {@code SELECT ... FOR UPDATE}, an application lock, or a queue.
 */
@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** A whole-day booking, resolved server-side in the office timezone. Never the client clock. */
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BookingStatus status = BookingStatus.ACTIVE;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    /**
     * Client-generated key carried on the claim request, under its own unique index. A double
     * click, a retry, or a flaky-network resend then returns the original result rather than a
     * confusing 409 against yourself.
     */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Booking(Seat seat, AppUser user, LocalDate bookingDate, String idempotencyKey) {
        this.seat = seat;
        this.user = user;
        this.bookingDate = bookingDate;
        this.idempotencyKey = idempotencyKey;
    }

    public boolean isActive() {
        return status == BookingStatus.ACTIVE;
    }
}
