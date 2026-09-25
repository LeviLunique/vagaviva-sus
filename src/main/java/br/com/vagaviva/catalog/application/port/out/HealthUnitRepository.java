package br.com.vagaviva.catalog.application.port.out;

import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase.HealthUnitFilter;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HealthUnitRepository {

    /** @throws br.com.vagaviva.shared.domain.ConflictException se o CNES já existir */
    HealthUnit save(HealthUnit unit);

    Optional<HealthUnit> findById(UUID id);

    boolean existsByCnes(Cnes cnes);

    Page<HealthUnit> search(HealthUnitFilter filter, Pageable pageable);
}
