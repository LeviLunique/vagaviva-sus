package br.com.vagaviva.scheduling.adapter.in.web;

import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.application.port.in.ExpireConfirmationsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-29: recurso de demonstração — só existe com {@code vagaviva.demo.enabled=true} (nunca em produção). */
@Tag(name = "Demonstração", description = "Recursos para demonstrar o fluxo sem esperar dias (desligados em produção)")
@RestController
@ConditionalOnBooleanProperty("vagaviva.demo.enabled")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
class SchedulingDemoController {

    private final ExpireConfirmationsUseCase expirations;
    private final SchedulingApi scheduling;

    SchedulingDemoController(ExpireConfirmationsUseCase expirations, SchedulingApi scheduling) {
        this.expirations = expirations;
        this.scheduling = scheduling;
    }

    @Operation(summary = "Expirar o prazo de confirmação agora (ADMIN, demonstração)",
            description = "Antecipa o prazo do agendamento para agora e aplica a expiração (RF-27): vaga liberada e paciente de volta à fila.")
    @ApiResponse(responseCode = "200", description = "Agendamento EXPIRED_UNCONFIRMED")
    @ApiResponse(responseCode = "404", description = "Inexistente (APPOINTMENT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "Não está aguardando confirmação (APPOINTMENT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/dev/appointments/{id}/expire-confirmation")
    @PreAuthorize("hasRole('ADMIN')")
    AppointmentView expire(@PathVariable UUID id) {
        expirations.expireNow(id);
        return scheduling.findAppointmentView(id).orElseThrow();
    }
}
