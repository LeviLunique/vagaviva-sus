package br.com.vagaviva.patient.application.port.out;

import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository {

    /** @throws br.com.vagaviva.shared.domain.ConflictException se CNS ou CPF já existirem */
    Patient save(Patient patient);

    Optional<Patient> findById(UUID id);

    Optional<Patient> findByCns(Cns cns);

    Optional<Patient> findByCpf(Cpf cpf);

    boolean existsByCns(Cns cns);

    boolean existsByCpf(Cpf cpf);
}
