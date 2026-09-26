package br.com.vagaviva.insights.application.port.out;

import br.com.vagaviva.insights.domain.PeriodCounts;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Agregações sobre os fatos no intervalo {@code [from, to)}. */
public interface IndicatorsReadModel {

    PeriodCounts counts(Instant from, Instant to, @Nullable UUID specialtyId, @Nullable UUID unitId);

    Map<UUID, PeriodCounts> countsBySpecialty(Instant from, Instant to);
}
