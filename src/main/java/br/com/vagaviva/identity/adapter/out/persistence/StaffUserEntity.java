package br.com.vagaviva.identity.adapter.out.persistence;

import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_user")
class StaffUserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "health_unit_id")
    private UUID healthUnitId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: versão nula = registro novo (persist); senão, merge com checagem. */
    @Version
    private Long version;

    protected StaffUserEntity() {
    }

    static StaffUserEntity from(StaffUser user) {
        var entity = new StaffUserEntity();
        entity.id = user.id();
        entity.name = user.name();
        entity.email = user.email();
        entity.passwordHash = user.passwordHash();
        entity.role = user.role();
        entity.healthUnitId = user.healthUnitId();
        entity.active = user.isActive();
        entity.failedLoginAttempts = user.failedLoginAttempts();
        entity.lockedUntil = user.lockedUntil();
        entity.createdAt = user.createdAt();
        entity.updatedAt = user.updatedAt();
        entity.version = user.version();
        return entity;
    }

    StaffUser toDomain() {
        return StaffUser.restore(id, name, email, passwordHash, role, healthUnitId, active, failedLoginAttempts,
                lockedUntil, createdAt, updatedAt, version);
    }
}
