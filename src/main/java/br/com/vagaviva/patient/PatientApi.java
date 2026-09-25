package br.com.vagaviva.patient;

import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de pacientes (Facade). */
public interface PatientApi {

    Optional<PatientSummary> findSummary(UUID patientId);
}
