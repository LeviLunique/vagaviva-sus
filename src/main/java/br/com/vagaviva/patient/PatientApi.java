package br.com.vagaviva.patient;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de pacientes (Facade). */
public interface PatientApi {

    Optional<PatientSummary> findSummary(UUID patientId);

    /** Consulta em lote (ex.: uma página da fila); ids inexistentes ficam fora do mapa. */
    Map<UUID, PatientSummary> findSummaries(Collection<UUID> patientIds);
}
