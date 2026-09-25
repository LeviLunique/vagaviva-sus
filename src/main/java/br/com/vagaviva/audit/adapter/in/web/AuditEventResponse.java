package br.com.vagaviva.audit.adapter.in.web;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.domain.AuditEvent;
import br.com.vagaviva.shared.security.Role;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        Role actorRole,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String clientIp,
        Map<String, String> details) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.id(), event.occurredAt(), event.actorId(), event.actorRole(),
                event.action(), event.resourceType(), event.resourceId(), event.outcome(), event.clientIp(),
                event.details());
    }
}
