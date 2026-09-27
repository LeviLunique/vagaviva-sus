package br.com.vagaviva.audit.adapter.out.persistence;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.domain.AuditEvent;
import br.com.vagaviva.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_event")
class AuditEventEntity {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20)
    private Role actorRole;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> details;

    protected AuditEventEntity() {
    }

    static AuditEventEntity from(AuditEvent event) {
        var entity = new AuditEventEntity();
        entity.id = event.id();
        entity.occurredAt = event.occurredAt();
        entity.actorId = event.actorId();
        entity.actorRole = event.actorRole();
        entity.action = event.action();
        entity.resourceType = event.resourceType();
        entity.resourceId = event.resourceId();
        entity.outcome = event.outcome();
        entity.clientIp = event.clientIp();
        entity.details = event.details().isEmpty() ? null : event.details();
        return entity;
    }

    AuditEvent toDomain() {
        return new AuditEvent(id, occurredAt, actorId, actorRole, action, resourceType, resourceId, outcome,
                clientIp, details);
    }
}
