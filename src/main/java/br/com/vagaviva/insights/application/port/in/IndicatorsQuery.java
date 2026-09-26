package br.com.vagaviva.insights.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RF-35: indicadores do gestor por período, especialidade e unidade. */
public interface IndicatorsQuery {

    /**
     * @throws br.com.vagaviva.shared.domain.BusinessRuleException {@code INVALID_PERIOD} ({@code from > to}) ou
     *     {@code PERIOD_TOO_LONG} (mais de 366 dias)
     */
    Indicators indicators(IndicatorFilter filter);

    List<SpecialtyIndicators> bySpecialty(LocalDate from, LocalDate to);

    /** Período em dias do fuso do negócio, com {@code from} e {@code to} inclusivos. */
    record IndicatorFilter(LocalDate from, LocalDate to, @Nullable UUID specialtyId, @Nullable UUID unitId) {
    }

    record AppointmentCounts(long scheduled, long confirmed, long expiredUnconfirmed, long cancelled, long attended,
            long noShows) {
    }

    record SlotCounts(long released, long reallocated, long lost) {
    }

    record Indicators(LocalDate from, LocalDate to, @Nullable UUID specialtyId, @Nullable UUID unitId,
            AppointmentCounts appointments, @Nullable BigDecimal absenteeismRate, @Nullable BigDecimal confirmationRate,
            SlotCounts slots, @Nullable BigDecimal reuseRate, @Nullable BigDecimal medianReoccupationHours,
            @Nullable BigDecimal avgWaitingDays, Map<String, Long> queueSizeByRisk,
            @Nullable Map<String, Long> messagesSent, @Nullable BigDecimal estimatedMessagingCostBrl) {
    }

    record SpecialtyIndicators(UUID specialtyId, String specialtyName, Indicators indicators) {
    }
}
