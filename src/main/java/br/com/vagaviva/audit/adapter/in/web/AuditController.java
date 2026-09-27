package br.com.vagaviva.audit.adapter.in.web;

import br.com.vagaviva.audit.application.port.in.AuditEventQuery;
import br.com.vagaviva.audit.application.port.in.SearchAuditEventsUseCase;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auditoria", description = "Trilha de auditoria de acessos e ações sensíveis (LGPD)")
@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasRole('ADMIN')")
class AuditController {

    private final SearchAuditEventsUseCase searchAuditEvents;

    AuditController(SearchAuditEventsUseCase searchAuditEvents) {
        this.searchAuditEvents = searchAuditEvents;
    }

    @Operation(summary = "Consultar a trilha de auditoria (ADMIN)",
            description = "Eventos mais recentes primeiro. Todos os filtros são opcionais; `from` é inclusivo e `to` exclusivo (ISO-8601).")
    @ApiResponse(responseCode = "200", description = "Página de eventos")
    @ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Período ou paginação inválidos", content = @Content(mediaType = "application/problem+json"))
    @GetMapping
    PageResponse<AuditEventResponse> search(
            @Parameter(description = "Tipo do recurso, ex.: STAFF_USER") @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) UUID actorId,
            @Parameter(example = "2026-09-01T00:00:00Z") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(example = "2026-10-01T00:00:00Z") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var query = new AuditEventQuery(resourceType, resourceId, actorId, from, to);
        return PageResponse.of(searchAuditEvents.search(query, PageRequest.of(page, size)), AuditEventResponse::from);
    }
}
