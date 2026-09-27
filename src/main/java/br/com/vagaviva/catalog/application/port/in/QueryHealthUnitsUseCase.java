package br.com.vagaviva.catalog.application.port.in;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.domain.HealthUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Consulta de unidades (qualquer profissional autenticado). */
public interface QueryHealthUnitsUseCase {

    /** @throws br.com.vagaviva.shared.domain.NotFoundException {@code HEALTH_UNIT_NOT_FOUND} */
    HealthUnit get(UUID id);

    /** Ordenado por nome. */
    Page<HealthUnit> list(HealthUnitFilter filter, Pageable pageable);

    record HealthUnitFilter(@Nullable HealthUnitType type, @Nullable String municipalityCode) {
    }
}
