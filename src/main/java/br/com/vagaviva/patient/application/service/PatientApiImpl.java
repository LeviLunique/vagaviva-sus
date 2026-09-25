package br.com.vagaviva.patient.application.service;

import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Patient;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chamadas internas entre módulos (sem ator humano): a auditoria fica com quem expõe o dado. */
@Service
@Transactional(readOnly = true)
class PatientApiImpl implements PatientApi {

    private final PatientRepository repository;
    private final Clock clock;

    PatientApiImpl(PatientRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<PatientSummary> findSummary(UUID patientId) {
        return repository.findById(patientId).map(this::summary);
    }

    private PatientSummary summary(Patient patient) {
        return new PatientSummary(patient.id(), patient.firstName(), patient.birthDate(),
                patient.municipalityCode().value(), patient.phone().value(), patient.preferredChannel(),
                patient.whatsappOptIn(), patient.isPriorityGroup(clock), patient.isActive());
    }
}
