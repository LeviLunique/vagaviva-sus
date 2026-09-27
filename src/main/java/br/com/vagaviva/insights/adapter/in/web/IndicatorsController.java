package br.com.vagaviva.insights.adapter.in.web;

import br.com.vagaviva.insights.application.port.in.IndicatorsQuery;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.IndicatorFilter;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.Indicators;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.SpecialtyIndicators;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Indicadores", description = "Indicadores do gestor (RF-35) — absenteísmo, confirmação, reaproveitamento, espera e fila")
@RestController
@RequestMapping("/api/v1/insights/indicators")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel diferente de MANAGER/ADMIN", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "422", description = "Período inválido (INVALID_PERIOD) ou maior que 366 dias (PERIOD_TOO_LONG)",
        content = @Content(mediaType = "application/problem+json"))
class IndicatorsController {

    private static final int DEFAULT_DAYS = 30;

    private final IndicatorsQuery indicators;
    private final Clock clock;

    IndicatorsController(IndicatorsQuery indicators, Clock clock) {
        this.indicators = indicators;
        this.clock = clock;
    }

    @Operation(summary = "Indicadores do período",
            description = "Datas (yyyy-MM-dd) inclusivas no fuso do negócio; padrão = últimos 30 dias. Agendamentos "
                    + "pela data do atendimento; liberações pela data em que a vaga foi liberada. Taxas em fração 0–1.")
    @ApiResponse(responseCode = "200", description = "Indicadores")
    @GetMapping
    Indicators indicators(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID specialtyId, @RequestParam(required = false) UUID unitId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        return indicators.indicators(new IndicatorFilter(from == null ? end.minusDays(DEFAULT_DAYS) : from, end,
                specialtyId, unitId));
    }

    @Operation(summary = "Indicadores por especialidade", description = "As mesmas métricas, uma entrada por especialidade.")
    @ApiResponse(responseCode = "200", description = "Indicadores por especialidade")
    @GetMapping("/by-specialty")
    List<SpecialtyIndicators> bySpecialty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        return indicators.bySpecialty(from == null ? end.minusDays(DEFAULT_DAYS) : from, end);
    }
}
