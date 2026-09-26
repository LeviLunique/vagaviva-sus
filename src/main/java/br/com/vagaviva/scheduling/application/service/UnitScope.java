package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RN-03 na agenda: SCHEDULER atua e enxerga apenas a própria unidade executante. */
final class UnitScope {

    private UnitScope() {
    }

    static void requireAccess(CurrentUser actor, UUID unitId) {
        if (actor.role() == Role.SCHEDULER && !unitId.equals(actor.unitId())) {
            throw SchedulingErrors.outOfUnit();
        }
    }

    /** Filtro efetivo de unidade: o do SCHEDULER é sempre a unidade dele. */
    static @Nullable UUID effectiveUnit(CurrentUser actor, @Nullable UUID requested) {
        return actor.role() == Role.SCHEDULER ? actor.unitId() : requested;
    }
}
