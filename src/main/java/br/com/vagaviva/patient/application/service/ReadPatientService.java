package br.com.vagaviva.patient.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.patient.application.port.in.ReadPatientUseCase;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leituras auditadas. Buscas sem resultado também ficam na trilha (tentativas de enumeração de
 * documentos) — por isso o 404 não desfaz a transação.
 */
@Service
class ReadPatientService implements ReadPatientUseCase {

    private final PatientRepository repository;
    private final PatientAudit audit;

    ReadPatientService(PatientRepository repository, PatientAudit audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Override
    @Transactional
    public Patient get(UUID patientId, CurrentUser actor, @Nullable String clientIp) {
        Patient patient = repository.findById(patientId).orElseThrow(PatientErrors::notFound);
        audit.record(actor, PatientAudit.PATIENT_READ, patient.id(), AuditOutcome.SUCCESS, clientIp,
                Map.of("via", "ID"));
        return patient;
    }

    @Override
    @Transactional(noRollbackFor = NotFoundException.class)
    public Patient search(SearchPatientQuery query, CurrentUser actor, @Nullable String clientIp) {
        boolean byCns = query.cns() != null && !query.cns().isBlank();
        boolean byCpf = query.cpf() != null && !query.cpf().isBlank();
        if (byCns == byCpf) {
            throw PatientErrors.invalidSearchCriteria();
        }
        String via = byCns ? "CNS" : "CPF";
        Optional<Patient> found = byCns
                ? repository.findByCns(Cns.of(query.cns()))
                : repository.findByCpf(Cpf.of(query.cpf()));
        if (found.isEmpty()) {
            audit.record(actor, PatientAudit.PATIENT_SEARCHED, null, AuditOutcome.FAILURE, clientIp, Map.of("via", via));
            throw PatientErrors.notFound();
        }
        audit.record(actor, PatientAudit.PATIENT_READ, found.get().id(), AuditOutcome.SUCCESS, clientIp,
                Map.of("via", "SEARCH_" + via));
        return found.get();
    }
}
