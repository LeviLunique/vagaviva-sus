package br.com.vagaviva.catalog.fixtures;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

/** Dados fictícios de catálogo para testes. */
public final class CatalogFixture {

    public static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    public static final MunicipalityCode SAO_PAULO = MunicipalityCode.of("3550308");
    public static final MunicipalityCode GUARULHOS = MunicipalityCode.of("3518800");

    private CatalogFixture() {
    }

    public static HealthUnit aPrimaryCareUnit() {
        return unit("1234567", HealthUnitType.PRIMARY_CARE, Set.of());
    }

    public static HealthUnit aSpecializedUnit(Set<MunicipalityCode> serviceArea) {
        return unit("7654321", HealthUnitType.SPECIALIZED, serviceArea);
    }

    public static HealthUnit unit(String cnes, HealthUnitType type, Set<MunicipalityCode> serviceArea) {
        return HealthUnit.register(Cnes.of(cnes), "UBS Jardim Fictício", type, SAO_PAULO, "São Paulo",
                "Rua das Flores, 100", serviceArea, CLOCK);
    }

    public static Specialty aSpecialty() {
        return Specialty.register("CARDIO", "Cardiologia", SpecialtyType.CONSULTATION, false, CLOCK);
    }
}
