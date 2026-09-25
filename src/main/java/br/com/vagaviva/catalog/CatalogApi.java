package br.com.vagaviva.catalog;

import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de catálogo (Facade) — único ponto de entrada síncrono. */
public interface CatalogApi {

    Optional<HealthUnitSummary> findUnit(UUID unitId);

    Optional<SpecialtySummary> findSpecialty(UUID specialtyId);

    /** A unidade atende pacientes do município? Área de atendimento vazia = atende todos. */
    boolean unitServes(UUID unitId, String municipalityCode);
}
