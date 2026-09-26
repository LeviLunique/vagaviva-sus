package br.com.vagaviva.insights.application.service;

import static br.com.vagaviva.insights.domain.IndicatorCalculator.absenteeismRate;
import static br.com.vagaviva.insights.domain.IndicatorCalculator.confirmationRate;
import static br.com.vagaviva.insights.domain.IndicatorCalculator.oneDecimal;
import static br.com.vagaviva.insights.domain.IndicatorCalculator.reuseRate;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.engagement.EngagementApi;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery;
import br.com.vagaviva.insights.application.port.out.IndicatorsReadModel;
import br.com.vagaviva.insights.domain.IndicatorCalculator;
import br.com.vagaviva.insights.domain.PeriodCounts;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.WaitingCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IndicatorsQueryService implements IndicatorsQuery {

    private static final long MAX_DAYS = 366;

    private final IndicatorsReadModel readModel;
    private final QueueApi queue;
    private final EngagementApi engagement;
    private final CatalogApi catalog;
    private final InsightsProperties properties;
    private final Clock clock;

    IndicatorsQueryService(IndicatorsReadModel readModel, QueueApi queue, EngagementApi engagement, CatalogApi catalog,
            InsightsProperties properties, Clock clock) {
        this.readModel = readModel;
        this.queue = queue;
        this.engagement = engagement;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Indicators indicators(IndicatorFilter filter) {
        validate(filter.from(), filter.to());
        Instant from = startOf(filter.from());
        Instant to = startOf(filter.to().plusDays(1));
        PeriodCounts counts = readModel.counts(from, to, filter.specialtyId(), filter.unitId());
        Map<String, Long> sent = engagement.sentByChannel(from, to);
        return build(filter, counts, queueByRisk(queue.waitingCounts(), filter.specialtyId()), sent,
                IndicatorCalculator.messagingCost(sent, properties.messagingCostBrl()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyIndicators> bySpecialty(LocalDate fromDay, LocalDate toDay) {
        validate(fromDay, toDay);
        Map<UUID, PeriodCounts> counts = readModel.countsBySpecialty(startOf(fromDay), startOf(toDay.plusDays(1)));
        List<WaitingCount> waiting = queue.waitingCounts();
        Set<UUID> specialties = new TreeSet<>(counts.keySet());
        waiting.forEach(w -> specialties.add(w.specialtyId()));
        return specialties.stream()
                .map(id -> new SpecialtyIndicators(id, catalog.findSpecialty(id).map(SpecialtySummary::name).orElse(""),
                        build(new IndicatorFilter(fromDay, toDay, id, null), counts.getOrDefault(id, PeriodCounts.empty()),
                                queueByRisk(waiting, id), null, null)))
                .sorted(Comparator.comparing(SpecialtyIndicators::specialtyName))
                .toList();
    }

    private static Indicators build(IndicatorFilter filter, PeriodCounts c, Map<String, Long> queueSize,
            @Nullable Map<String, Long> sent, @Nullable BigDecimal cost) {
        return new Indicators(filter.from(), filter.to(), filter.specialtyId(), filter.unitId(),
                new AppointmentCounts(c.scheduled(), c.confirmed(), c.expiredUnconfirmed(), c.cancelled(), c.attended(),
                        c.noShows()),
                absenteeismRate(c), confirmationRate(c), new SlotCounts(c.released(), c.reallocated(), c.lost()),
                reuseRate(c), oneDecimal(c.medianReoccupationHours()), oneDecimal(c.avgWaitingDays()), queueSize, sent,
                cost);
    }

    /** Todas as classes de risco aparecem (zero quando vazias), na ordem de prioridade. */
    private static Map<String, Long> queueByRisk(List<WaitingCount> waiting, @Nullable UUID specialtyId) {
        Map<String, Long> byRisk = new LinkedHashMap<>();
        for (RiskClass risk : RiskClass.values()) {
            byRisk.put(risk.name(), waiting.stream()
                    .filter(w -> w.riskClass() == risk && (specialtyId == null || Objects.equals(w.specialtyId(), specialtyId)))
                    .mapToLong(WaitingCount::waiting).sum());
        }
        return byRisk;
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw InsightsErrors.invalidPeriod();
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw InsightsErrors.periodTooLong();
        }
    }

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(clock.getZone()).toInstant();
    }
}
