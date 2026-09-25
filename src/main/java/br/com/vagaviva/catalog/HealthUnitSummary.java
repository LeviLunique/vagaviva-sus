package br.com.vagaviva.catalog;

import java.util.Set;
import java.util.UUID;

/** Visão pública de uma unidade de saúde para outros módulos; {@code serviceArea} vazia = atende todos. */
public record HealthUnitSummary(UUID id, String cnes, String name, HealthUnitType type, String municipalityCode,
        String municipalityName, String address, Set<String> serviceArea, boolean active) {

    public HealthUnitSummary {
        serviceArea = serviceArea == null ? Set.of() : Set.copyOf(serviceArea);
    }
}
