package br.com.vagaviva.patient.adapter.out.persistence;

import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.domain.ConflictException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class PatientPersistenceAdapter implements PatientRepository {

    private static final String CNS_CONSTRAINT = "patient_cns_key";
    private static final String CPF_CONSTRAINT = "patient_cpf_key";

    private final PatientJpaRepository repository;

    PatientPersistenceAdapter(PatientJpaRepository repository) {
        this.repository = repository;
    }

    /** Corrida entre dois cadastros do mesmo documento: os índices únicos decidem (RF-08 CA1). */
    @Override
    public Patient save(Patient patient) {
        try {
            return repository.saveAndFlush(PatientEntity.from(patient)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (cause.contains(CNS_CONSTRAINT)) {
                throw new ConflictException("CNS_ALREADY_REGISTERED", "Já existe um paciente com este CNS.");
            }
            if (cause.contains(CPF_CONSTRAINT)) {
                throw new ConflictException("CPF_ALREADY_REGISTERED", "Já existe um paciente com este CPF.");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        return repository.findById(id).map(PatientEntity::toDomain);
    }

    @Override
    public Optional<Patient> findByCns(Cns cns) {
        return repository.findByCns(cns.value()).map(PatientEntity::toDomain);
    }

    @Override
    public Optional<Patient> findByCpf(Cpf cpf) {
        return repository.findByCpf(cpf.value()).map(PatientEntity::toDomain);
    }

    @Override
    public boolean existsByCns(Cns cns) {
        return repository.existsByCns(cns.value());
    }

    @Override
    public boolean existsByCpf(Cpf cpf) {
        return repository.existsByCpf(cpf.value());
    }
}
