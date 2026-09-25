package br.com.vagaviva.catalog;

import java.util.UUID;

/** Visão pública de uma unidade de saúde para outros módulos. */
public record HealthUnitSummary(UUID id, String cnes, String name, HealthUnitType type, String municipalityCode,
        String municipalityName, String address, boolean active) {
}
