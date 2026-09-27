package br.com.vagaviva.catalog.application.service;

import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase;
import br.com.vagaviva.catalog.application.port.in.RegisterHealthUnitUseCase;
import br.com.vagaviva.catalog.application.port.in.ReplaceServiceAreaUseCase;
import br.com.vagaviva.catalog.application.port.out.HealthUnitRepository;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do agregado {@link HealthUnit} (cadastro, consulta e área de atendimento). */
@Service
class HealthUnitService implements RegisterHealthUnitUseCase, QueryHealthUnitsUseCase, ReplaceServiceAreaUseCase {

    private final HealthUnitRepository repository;
    private final Clock clock;

    HealthUnitService(HealthUnitRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public HealthUnit register(RegisterHealthUnitCommand command) {
        Cnes cnes = Cnes.of(command.cnes());
        if (repository.existsByCnes(cnes)) {
            throw CatalogErrors.cnesAlreadyRegistered();
        }
        return repository.save(HealthUnit.register(cnes, command.name(), command.type(),
                MunicipalityCode.of(command.municipalityCode()), command.municipalityName(), command.address(),
                municipalities(command.serviceArea()), clock));
    }

    @Override
    @Transactional(readOnly = true)
    public HealthUnit get(UUID id) {
        return repository.findById(id).orElseThrow(CatalogErrors::unitNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HealthUnit> list(HealthUnitFilter filter, Pageable pageable) {
        return repository.search(filter, pageable);
    }

    @Override
    @Transactional
    public HealthUnit replace(UUID unitId, Set<String> municipalityCodes) {
        HealthUnit unit = repository.findById(unitId).orElseThrow(CatalogErrors::unitNotFound);
        unit.replaceServiceArea(municipalities(municipalityCodes), clock);
        return repository.save(unit);
    }

    private static Set<MunicipalityCode> municipalities(Set<String> codes) {
        return codes == null ? Set.of() : codes.stream().map(MunicipalityCode::of).collect(Collectors.toSet());
    }
}
