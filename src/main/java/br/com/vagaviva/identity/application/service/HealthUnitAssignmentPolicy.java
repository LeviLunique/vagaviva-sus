package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * RN-03: a unidade vinculada ao profissional precisa existir, estar ativa e ser do tipo que o
 * papel exige — REQUESTER atua por UBS ({@code PRIMARY_CARE}), SCHEDULER por unidade executante
 * ({@code SPECIALIZED}). A obrigatoriedade da unidade já é garantida pelo domínio.
 */
@Component
class HealthUnitAssignmentPolicy {

    private static final Map<Role, HealthUnitType> REQUIRED_TYPE = Map.of(
            Role.REQUESTER, HealthUnitType.PRIMARY_CARE,
            Role.SCHEDULER, HealthUnitType.SPECIALIZED);

    private final CatalogApi catalog;

    HealthUnitAssignmentPolicy(CatalogApi catalog) {
        this.catalog = catalog;
    }

    void check(Role role, @Nullable UUID healthUnitId) {
        if (healthUnitId == null) {
            return;
        }
        HealthUnitSummary unit = catalog.findUnit(healthUnitId).orElseThrow(() -> new BusinessRuleException(
                "HEALTH_UNIT_NOT_FOUND", "A unidade de saúde informada (healthUnitId) não existe."));
        if (!unit.active()) {
            throw new BusinessRuleException("HEALTH_UNIT_INACTIVE", "A unidade de saúde informada está inativa.");
        }
        HealthUnitType required = REQUIRED_TYPE.get(role);
        if (required != null && unit.type() != required) {
            throw new BusinessRuleException("HEALTH_UNIT_TYPE_MISMATCH",
                    "O papel %s exige uma unidade do tipo %s.".formatted(role, required));
        }
    }
}
