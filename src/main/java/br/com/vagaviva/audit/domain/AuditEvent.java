package br.com.vagaviva.audit.domain;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.shared.domain.Ids;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Evento de auditoria gravado — imutável (a trilha é append-only). */
public record AuditEvent(
        UUID id,
        Instant occurredAt,
        @Nullable UUID actorId,
        @Nullable Role actorRole,
        String action,
        String resourceType,
        @Nullable String resourceId,
        AuditOutcome outcome,
        @Nullable String clientIp,
        Map<String, String> details) {

    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AuditEvent occurred(@Nullable UUID actorId, @Nullable Role actorRole, String action,
            String resourceType, @Nullable String resourceId, AuditOutcome outcome, @Nullable String clientIp,
            Map<String, String> details, Clock clock) {
        return new AuditEvent(Ids.newId(), clock.instant(), actorId, actorRole, action, resourceType, resourceId,
                outcome, clientIp, details);
    }
}
