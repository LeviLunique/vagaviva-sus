package br.com.vagaviva.catalog.adapter.in.web;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de saída do catálogo. */
final class CatalogResponses {

    private CatalogResponses() {
    }

    record HealthUnitResponse(UUID id, String cnes, String name, HealthUnitType type, String municipalityCode,
            String municipalityName, String address, List<String> serviceArea, boolean active, Instant createdAt) {

        static HealthUnitResponse from(HealthUnit unit) {
            return new HealthUnitResponse(unit.id(), unit.cnes().value(), unit.name(), unit.type(),
                    unit.municipalityCode().value(), unit.municipalityName(), unit.address(),
                    unit.serviceArea().stream().map(MunicipalityCode::value).sorted().toList(), unit.isActive(),
                    unit.createdAt());
        }
    }

    record SpecialtyResponse(UUID id, String code, String name, SpecialtyType type, boolean sensitive,
            boolean active) {

        static SpecialtyResponse from(Specialty specialty) {
            return new SpecialtyResponse(specialty.id(), specialty.code(), specialty.name(), specialty.type(),
                    specialty.sensitive(), specialty.active());
        }
    }
}
