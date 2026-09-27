package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.AuditTrail;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Auditoria da regulação (RN-25): escritas e leituras de encaminhamentos e da fila, sem dados clínicos. */
@Component
class RegulationAudit {

    static final String REFERRAL = "REFERRAL";
    static final String QUEUE = "QUEUE";
    static final String REFERRAL_CREATED = "REFERRAL_CREATED";
    static final String REFERRAL_READ = "REFERRAL_READ";
    static final String REFERRAL_REGULATED = "REFERRAL_REGULATED";
    static final String REFERRAL_RESUBMITTED = "REFERRAL_RESUBMITTED";
    static final String REFERRAL_CANCELLED = "REFERRAL_CANCELLED";
    static final String QUEUE_READ = "QUEUE_READ";
    static final String QUEUE_SNAPSHOT_REFRESHED = "QUEUE_SNAPSHOT_REFRESHED";
    static final String PUBLIC_POSITION_READ = "PUBLIC_QUEUE_POSITION_READ";

    private final AuditTrail auditTrail;

    RegulationAudit(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    void record(@Nullable CurrentUser actor, String action, String resourceType, @Nullable UUID resourceId,
            AuditOutcome outcome, @Nullable String clientIp, Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor == null ? null : actor.id(), actor == null ? null : actor.role(),
                action, resourceType, resourceId == null ? null : resourceId.toString(), outcome, clientIp, details));
    }
}
