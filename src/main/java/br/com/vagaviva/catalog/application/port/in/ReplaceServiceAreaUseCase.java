package br.com.vagaviva.catalog.application.port.in;

import br.com.vagaviva.catalog.domain.HealthUnit;
import java.util.Set;
import java.util.UUID;

/** RF-06: substitui a lista de municípios atendidos pela unidade. */
public interface ReplaceServiceAreaUseCase {

    HealthUnit replace(UUID unitId, Set<String> municipalityCodes);
}
