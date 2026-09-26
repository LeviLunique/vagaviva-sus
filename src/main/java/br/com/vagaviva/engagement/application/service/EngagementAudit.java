package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.AuditTrail;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Ações do paciente pelo link (RF-26): o ator é o paciente (sem usuário) e o detalhe é o id do token. */
@Component
class EngagementAudit {

    static final String APPOINTMENT = "APPOINTMENT";
    static final String SLOT_OFFER = "SLOT_OFFER";
    static final String PATIENT_LINK = "PATIENT_LINK";
    static final String LINK_VIEWED = "PATIENT_LINK_VIEWED";
    static final String LINK_REJECTED = "PATIENT_LINK_REJECTED";
    static final String PATIENT_CONFIRMED = "PATIENT_CONFIRMED";
    static final String PATIENT_CANCELLED = "PATIENT_CANCELLED";
    static final String PATIENT_WITHDREW = "PATIENT_WITHDREW";
    static final String PATIENT_ACCEPTED_OFFER = "PATIENT_ACCEPTED_OFFER";
    static final String PATIENT_DECLINED_OFFER = "PATIENT_DECLINED_OFFER";

    private final AuditTrail auditTrail;

    EngagementAudit(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    void patientAction(String action, UUID appointmentId, UUID tokenId, @Nullable String clientIp) {
        auditTrail.record(new AuditEntry(null, null, action, APPOINTMENT, appointmentId.toString(), AuditOutcome.SUCCESS,
                clientIp, Map.of("tokenId", tokenId.toString())));
    }

    void offerAction(String action, UUID offerId, UUID tokenId, @Nullable String clientIp) {
        auditTrail.record(new AuditEntry(null, null, action, SLOT_OFFER, offerId.toString(), AuditOutcome.SUCCESS,
                clientIp, Map.of("tokenId", tokenId.toString())));
    }

    /** Link inexistente ou expirado — tentativas de adivinhar tokens ficam visíveis. */
    void rejectedLink(String reason, @Nullable String clientIp) {
        auditTrail.record(new AuditEntry(null, null, LINK_REJECTED, PATIENT_LINK, null, AuditOutcome.DENIED, clientIp,
                Map.of("reason", reason)));
    }
}
