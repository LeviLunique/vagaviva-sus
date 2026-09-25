package br.com.vagaviva.catalog.application.port.in;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.domain.HealthUnit;
import java.util.Set;

/** RF-06: ADMIN cadastra unidade de saúde (CNES único). */
public interface RegisterHealthUnitUseCase {

    HealthUnit register(RegisterHealthUnitCommand command);

    record RegisterHealthUnitCommand(String cnes, String name, HealthUnitType type, String municipalityCode,
            String municipalityName, String address, Set<String> serviceArea) {
    }
}
