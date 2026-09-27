package br.com.vagaviva.regulation.adapter.in.web;

import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase;
import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase.PublicQueuePosition;
import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase.PublicSpecialtyStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transparência (público)", description = "Consulta do cidadão e estatísticas públicas da fila (RF-17, RF-18) — sem login")
@RestController
@RequestMapping("/api/v1/public")
@SecurityRequirements
class PublicTransparencyController {

    private final PublicTransparencyUseCase transparency;

    PublicTransparencyController(PublicTransparencyUseCase transparency) {
        this.transparency = transparency;
    }

    @Operation(summary = "Minha posição na fila",
            description = "Protocolo + data de nascimento no corpo (dados pessoais nunca na URL). Posição e tempo estimado vêm do snapshot (a cada 5 min).")
    @ApiResponse(responseCode = "200", description = "Situação do encaminhamento")
    @ApiResponse(responseCode = "404", description = "Resposta genérica para protocolo inexistente ou data divergente (QUEUE_POSITION_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/queue-position")
    PublicQueuePosition position(@Valid @RequestBody QueuePositionRequest request, HttpServletRequest http) {
        return transparency.position(request.protocol(), request.birthDate(), http.getRemoteAddr());
    }

    @Operation(summary = "Estatísticas públicas da fila", description = "Por especialidade: aguardando por classe de risco, espera média e vazão (PNR-SUS arts. 38 e 56).")
    @ApiResponse(responseCode = "200", description = "Estatísticas do último snapshot")
    @GetMapping("/queue-stats")
    List<PublicSpecialtyStats> stats() {
        return transparency.stats();
    }

    record QueuePositionRequest(
            @Schema(example = "VV-2026-0000123") @NotBlank @Size(max = 20) String protocol,
            @Schema(example = "1958-03-14") @NotNull LocalDate birthDate) {
    }
}
