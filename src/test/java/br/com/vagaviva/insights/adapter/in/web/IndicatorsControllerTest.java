package br.com.vagaviva.insights.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.insights.application.port.in.IndicatorsQuery;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.AppointmentCounts;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.IndicatorFilter;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.Indicators;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.SlotCounts;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.SpecialtyIndicators;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(IndicatorsController.class)
@Import({WebSliceSecurity.class, IndicatorsControllerTest.FixedClock.class})
class IndicatorsControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean IndicatorsQuery indicators;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    private static Indicators sample(LocalDate from, LocalDate to) {
        return new Indicators(from, to, null, null, new AppointmentCounts(5, 3, 1, 1, 2, 1), new BigDecimal("0.3333"),
                new BigDecimal("0.7500"), new SlotCounts(2, 1, 1), new BigDecimal("0.5000"), new BigDecimal("3.5"), null,
                Map.of("RED", 4L), Map.of("SANDBOX", 9L), new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("RF-35: MANAGER consulta; sem datas ⇒ últimos 30 dias até hoje (São Paulo)")
    void shouldDefaultToLast30Days() {
        LocalDate today = LocalDate.of(2026, 9, 26);
        when(indicators.indicators(any())).thenReturn(sample(today.minusDays(30), today));

        var result = mvc.get().uri("/api/v1/insights/indicators").with(TestJwt.as(Role.MANAGER)).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.absenteeismRate").isEqualTo(0.3333);
        assertThat(result).bodyJson().extractingPath("$.appointments.confirmed").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.avgWaitingDays").isNull();
        verify(indicators).indicators(new IndicatorFilter(today.minusDays(30), today, null, null));
    }

    @Test
    @DisplayName("filtros explícitos; por especialidade; REQUESTER ⇒ 403; sem token ⇒ 401; período inválido ⇒ 422")
    void shouldApplyFiltersAndRules() {
        UUID specialty = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(indicators.indicators(eq(new IndicatorFilter(from, to, specialty, null)))).thenReturn(sample(from, to));
        when(indicators.bySpecialty(from, to)).thenReturn(List.of(new SpecialtyIndicators(specialty, "Cardiologia",
                sample(from, to))));
        when(indicators.indicators(eq(new IndicatorFilter(to, from, null, null))))
                .thenThrow(new BusinessRuleException("INVALID_PERIOD", "A data inicial deve ser anterior ou igual à final."));

        assertThat(mvc.get().uri("/api/v1/insights/indicators?from=2026-09-01&to=2026-09-30&specialtyId={s}", specialty)
                .with(TestJwt.as(Role.ADMIN))).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/insights/indicators/by-specialty?from=2026-09-01&to=2026-09-30")
                .with(TestJwt.as(Role.MANAGER))).hasStatusOk().bodyJson().extractingPath("$[0].specialtyName")
                .isEqualTo("Cardiologia");
        assertThat(mvc.get().uri("/api/v1/insights/indicators?from=2026-09-30&to=2026-09-01").with(TestJwt.as(Role.MANAGER)))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("INVALID_PERIOD");
        assertThat(mvc.get().uri("/api/v1/insights/indicators").with(TestJwt.as(Role.REQUESTER)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/insights/indicators/by-specialty")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("data em formato inválido ⇒ 400, sem chamar a consulta")
    void shouldRejectMalformedDate() {
        assertThat(mvc.get().uri("/api/v1/insights/indicators?from=26/09/2026").with(TestJwt.as(Role.MANAGER)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(indicators);
    }
}
