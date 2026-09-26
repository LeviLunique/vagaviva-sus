package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.AuditTrail;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Trilha do encaixe: ator do sistema (o paciente age pelo link e é auditado no engajamento). */
@Component
class ReallocationAudit {

    static final String SLOT = "SLOT";
    static final String SLOT_OFFER = "SLOT_OFFER";
    static final String OFFER_ROUND_STARTED = "OFFER_ROUND_STARTED";
    static final String OFFER_ACCEPTED = "OFFER_ACCEPTED";
    static final String OFFER_DECLINED = "OFFER_DECLINED";

    private final AuditTrail auditTrail;

    ReallocationAudit(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    void record(String action, String resourceType, UUID resourceId, Map<String, String> details) {
        auditTrail.record(new AuditEntry(null, null, action, resourceType, resourceId.toString(), AuditOutcome.SUCCESS,
                null, details));
    }
}
