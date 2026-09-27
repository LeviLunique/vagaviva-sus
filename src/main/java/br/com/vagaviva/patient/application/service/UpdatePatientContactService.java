package br.com.vagaviva.patient.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.patient.application.port.in.UpdatePatientContactUseCase;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdatePatientContactService implements UpdatePatientContactUseCase {

    private final PatientRepository repository;
    private final PatientAudit audit;
    private final Clock clock;

    UpdatePatientContactService(PatientRepository repository, PatientAudit audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Patient update(UpdatePatientContactCommand command) {
        PhoneNumber phone = command.phone() == null ? null : PhoneNumber.of(command.phone());
        Patient patient = repository.findById(command.patientId()).orElseThrow(PatientErrors::notFound);
        boolean changed = patient.updateContact(phone, command.preferredChannel(), command.whatsappOptIn(), clock);
        Patient result = changed ? repository.save(patient) : patient;
        audit.record(command.actor(), PatientAudit.PATIENT_CONTACT_UPDATED, patient.id(), AuditOutcome.SUCCESS,
                command.clientIp(), Map.of("changed", String.valueOf(changed)));
        return result;
    }
}
