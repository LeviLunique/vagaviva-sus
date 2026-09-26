package br.com.vagaviva.catalog.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.catalog.application.port.out.HealthUnitRepository;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogApiImpl implements CatalogApi {

    private final HealthUnitRepository units;
    private final SpecialtyRepository specialties;

    CatalogApiImpl(HealthUnitRepository units, SpecialtyRepository specialties) {
        this.units = units;
        this.specialties = specialties;
    }

    @Override
    public Optional<HealthUnitSummary> findUnit(UUID unitId) {
        return units.findById(unitId).map(CatalogApiImpl::summary);
    }

    @Override
    public Optional<SpecialtySummary> findSpecialty(UUID specialtyId) {
        return specialties.findById(specialtyId).map(CatalogApiImpl::summary);
    }

    @Override
    public boolean unitServes(UUID unitId, String municipalityCode) {
        MunicipalityCode municipality = MunicipalityCode.of(municipalityCode);
        return units.findById(unitId).map(unit -> unit.serves(municipality)).orElse(false);
    }

    private static HealthUnitSummary summary(HealthUnit unit) {
        return new HealthUnitSummary(unit.id(), unit.cnes().value(), unit.name(), unit.type(),
                unit.municipalityCode().value(), unit.municipalityName(), unit.address(),
                unit.serviceArea().stream().map(MunicipalityCode::value).collect(Collectors.toSet()), unit.isActive());
    }

    private static SpecialtySummary summary(Specialty specialty) {
        return new SpecialtySummary(specialty.id(), specialty.code(), specialty.name(), specialty.type(),
                specialty.sensitive(), specialty.active());
    }
}
