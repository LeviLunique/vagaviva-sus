package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.AuditTrail;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Auditoria da agenda (RN-25). Ações do motor de alocação ficam sem ator humano. */
@Component
class SchedulingAudit {

    static final String SLOT = "SLOT";
    static final String APPOINTMENT = "APPOINTMENT";
    static final String SLOTS_PUBLISHED = "SLOTS_PUBLISHED";
    static final String SLOT_CANCELLED = "SLOT_CANCELLED";
    static final String APPOINTMENT_SCHEDULED = "APPOINTMENT_SCHEDULED";
    static final String APPOINTMENT_READ = "APPOINTMENT_READ";
    static final String APPOINTMENT_CANCELLED_BY_UNIT = "APPOINTMENT_CANCELLED_BY_UNIT";
    static final String APPOINTMENT_ATTENDED = "APPOINTMENT_ATTENDED";
    static final String APPOINTMENT_NO_SHOW = "APPOINTMENT_NO_SHOW";
    static final String APPOINTMENT_EXPIRED = "APPOINTMENT_EXPIRED";
    static final String SLOT_RELEASED = "SLOT_RELEASED";

    private final AuditTrail auditTrail;

    SchedulingAudit(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    void record(@Nullable CurrentUser actor, String action, String resourceType, @Nullable UUID resourceId,
            @Nullable String clientIp, Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor == null ? null : actor.id(), actor == null ? null : actor.role(),
                action, resourceType, resourceId == null ? null : resourceId.toString(), AuditOutcome.SUCCESS, clientIp,
                details));
    }
}
