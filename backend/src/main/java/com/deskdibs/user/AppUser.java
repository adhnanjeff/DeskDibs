package com.deskdibs.user;

import com.deskdibs.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends BaseEntity {

    /** Microsoft Entra ID {@code oid}. Null until the user has signed in through SSO. */
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * BCrypt hash, local development provider only.
     *
     * <p>Null for every Entra-authenticated user — their credential lives in the tenant and never
     * reaches this database. Null therefore means "this account cannot log in with a password",
     * which is the correct answer, not a missing value.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.EMPLOYEE;

    /** Deactivated users keep their history but are refused at login. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public AppUser(String email, String displayName, UserRole role) {
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }
}
