package br.com.vagaviva.patient.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.AuditTrail;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Auditoria de acesso a dados de pacientes (RF-05, RN-25) — registra quem, o quê e de onde, mas
 * nunca o CNS/CPF pesquisado nem outros dados pessoais.
 */
@Component
class PatientAudit {

    static final String RESOURCE_TYPE = "PATIENT";
    static final String PATIENT_CREATED = "PATIENT_CREATED";
    static final String PATIENT_READ = "PATIENT_READ";
    static final String PATIENT_SEARCHED = "PATIENT_SEARCHED";
    static final String PATIENT_CONTACT_UPDATED = "PATIENT_CONTACT_UPDATED";

    private final AuditTrail auditTrail;

    PatientAudit(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    void record(CurrentUser actor, String action, @Nullable UUID patientId, AuditOutcome outcome,
            @Nullable String clientIp, Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.id(), actor.role(), action, RESOURCE_TYPE,
                patientId == null ? null : patientId.toString(), outcome, clientIp, details));
    }
}
