package br.com.vagaviva.insights.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.EngagementApi;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.IndicatorFilter;
import br.com.vagaviva.insights.application.port.out.IndicatorsReadModel;
import br.com.vagaviva.insights.domain.PeriodCounts;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.WaitingCount;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndicatorsQueryServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), SP);
    private static final UUID CARDIO = UUID.randomUUID();
    private static final UUID PSIQ = UUID.randomUUID();

    @Mock IndicatorsReadModel readModel;
    @Mock QueueApi queue;
    @Mock EngagementApi engagement;
    @Mock CatalogApi catalog;

    private IndicatorsQueryService service;

    @BeforeEach
    void setUp() {
        service = new IndicatorsQueryService(readModel, queue, engagement, catalog, new InsightsProperties(
                Map.of("SMS", new BigDecimal("0.16"), "WHATSAPP", new BigDecimal("0.05"), "SANDBOX", BigDecimal.ZERO)),
                CLOCK);
    }

    @Test
    @DisplayName("RF-35: período em dias de São Paulo (fim inclusivo), fila por risco com zeros e custo das mensagens")
    void shouldBuildIndicators() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        Instant start = Instant.parse("2026-09-01T03:00:00Z");
        Instant end = Instant.parse("2026-10-01T03:00:00Z");
        when(readModel.counts(start, end, CARDIO, null)).thenReturn(new PeriodCounts(10, 6, 2, 1, 3, 1, 42.25, 4, 3, 1, 5.04));
        when(engagement.sentByChannel(start, end)).thenReturn(Map.of("SMS", 10L, "SANDBOX", 4L));
        when(queue.waitingCounts()).thenReturn(List.of(new WaitingCount(CARDIO, RiskClass.RED, 2),
                new WaitingCount(CARDIO, RiskClass.GREEN, 5), new WaitingCount(PSIQ, RiskClass.RED, 7)));

        var result = service.indicators(new IndicatorFilter(from, to, CARDIO, null));

        assertThat(result.appointments().scheduled()).isEqualTo(10);
        assertThat(result.absenteeismRate()).isEqualByComparingTo("0.2500");
        assertThat(result.confirmationRate()).isEqualByComparingTo("0.7500");
        assertThat(result.reuseRate()).isEqualByComparingTo("0.7500");
        assertThat(result.avgWaitingDays()).isEqualByComparingTo("42.3");
        assertThat(result.medianReoccupationHours()).isEqualByComparingTo("5.0");
        assertThat(result.queueSizeByRisk()).containsExactly(Map.entry("RED", 2L), Map.entry("YELLOW", 0L),
                Map.entry("GREEN", 5L), Map.entry("BLUE", 0L));
        assertThat(result.estimatedMessagingCostBrl()).isEqualByComparingTo("1.60");
        assertThat(result.messagesSent()).containsEntry("SANDBOX", 4L);
    }

    @Test
    @DisplayName("422: início depois do fim ou período maior que 366 dias")
    void shouldValidatePeriod() {
        assertThatThrownBy(() -> service.indicators(new IndicatorFilter(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1),
                null, null))).isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_PERIOD");
        assertThatThrownBy(() -> service.bySpecialty(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 9, 2)))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("PERIOD_TOO_LONG");
        when(readModel.counts(any(), any(), any(), any())).thenReturn(PeriodCounts.empty());
        var limit = service.indicators(new IndicatorFilter(LocalDate.of(2025, 9, 27), LocalDate.of(2026, 9, 27), null, null));
        assertThat(limit.estimatedMessagingCostBrl()).isEqualByComparingTo("0.00");
        assertThat(limit.absenteeismRate()).isNull();
        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("por especialidade: especialidades com fatos ou fila, em ordem de nome; sem custo de mensagens")
    void shouldGroupBySpecialty() {
        when(readModel.countsBySpecialty(any(), any())).thenReturn(Map.of(CARDIO,
                new PeriodCounts(4, 2, 0, 0, 1, 1, null, 0, 0, 0, null)));
        when(queue.waitingCounts()).thenReturn(List.of(new WaitingCount(PSIQ, RiskClass.YELLOW, 3)));
        when(catalog.findSpecialty(CARDIO)).thenReturn(Optional.of(new SpecialtySummary(CARDIO, "CARD", "Cardiologia",
                SpecialtyType.CONSULTATION, false, true)));
        when(catalog.findSpecialty(PSIQ)).thenReturn(Optional.empty());

        var result = service.bySpecialty(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result).extracting(r -> r.specialtyName()).containsExactly("", "Cardiologia");
        assertThat(result.get(1).indicators().absenteeismRate()).isEqualByComparingTo("0.5000");
        assertThat(result.get(1).indicators().estimatedMessagingCostBrl()).isNull();
        assertThat(result.get(0).indicators().queueSizeByRisk()).containsEntry("YELLOW", 3L);
        assertThat(result.get(0).indicators().reuseRate()).isNull();
    }
}
