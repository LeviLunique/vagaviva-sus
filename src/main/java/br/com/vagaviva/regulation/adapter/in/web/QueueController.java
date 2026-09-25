package br.com.vagaviva.regulation.adapter.in.web;

import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.QueueItem;
import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase;
import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase.SnapshotResult;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fila", description = "Fila priorizada por especialidade (RF-16, RN-06)")
@RestController
@RequestMapping("/api/v1/queues")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel sem permissão", content = @Content(mediaType = "application/problem+json"))
class QueueController {

    private final QueryReferralsUseCase queries;
    private final QueueSnapshotUseCase snapshot;
    private final CurrentUserProvider currentUser;

    QueueController(QueryReferralsUseCase queries, QueueSnapshotUseCase snapshot, CurrentUserProvider currentUser) {
        this.queries = queries;
        this.snapshot = snapshot;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Fila da especialidade (REGULATOR, MANAGER, ADMIN)",
            description = "Ordem oficial: risco, grupo prioritário, data de entrada. Paciente mascarado (primeiro nome e CNS). Leitura auditada.")
    @ApiResponse(responseCode = "200", description = "Página da fila; `position` é a posição real na fila")
    @ApiResponse(responseCode = "404", description = "Especialidade inexistente (SPECIALTY_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{specialtyId}")
    @PreAuthorize("hasAnyRole('REGULATOR','MANAGER','ADMIN')")
    PageResponse<QueueItem> queue(@PathVariable UUID specialtyId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest http) {
        return PageResponse.of(queries.queue(specialtyId, PageRequest.of(page, size), currentUser.get(),
                http.getRemoteAddr()), item -> item);
    }

    @Operation(summary = "Recalcular o snapshot da fila agora (ADMIN)",
            description = "O snapshot usado pela consulta pública é recalculado a cada 5 minutos; esta operação antecipa o recálculo.")
    @ApiResponse(responseCode = "200", description = "Snapshot recalculado")
    @PostMapping("/snapshot")
    @PreAuthorize("hasRole('ADMIN')")
    SnapshotResult refreshSnapshot() {
        return snapshot.refresh();
    }
}
