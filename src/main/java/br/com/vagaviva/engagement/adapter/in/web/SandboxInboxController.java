package br.com.vagaviva.engagement.adapter.in.web;

import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationStatus;
import br.com.vagaviva.engagement.domain.NotificationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-29: caixa de entrada do canal SANDBOX — só com {@code vagaviva.demo.enabled=true} (nunca em produção). */
@Tag(name = "Demonstração")
@RestController
@ConditionalOnBooleanProperty("vagaviva.demo.enabled")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
class SandboxInboxController {

    private final NotificationQueryUseCases queries;

    SandboxInboxController(NotificationQueryUseCases queries) {
        this.queries = queries;
    }

    @Operation(summary = "Mensagens sandbox do paciente (ADMIN, demonstração)",
            description = "Texto enviado, com o link — é por aqui que a demonstração \"recebe\" o SMS.")
    @ApiResponse(responseCode = "200", description = "Mensagens, mais recentes primeiro")
    @GetMapping("/api/v1/dev/sandbox/messages")
    @PreAuthorize("hasRole('ADMIN')")
    List<SandboxMessage> inbox(@RequestParam UUID patientId, @RequestParam(defaultValue = "20") int limit) {
        return queries.sandboxInbox(patientId, limit).stream().map(SandboxMessage::from).toList();
    }

    record SandboxMessage(UUID id, UUID patientId, UUID appointmentId, NotificationType type, NotificationStatus status,
            String body, Instant createdAt, Instant sentAt) {

        static SandboxMessage from(Notification n) {
            return new SandboxMessage(n.id(), n.patientId(), n.appointmentId(), n.type(), n.status(), n.body(),
                    n.createdAt(), n.sentAt());
        }
    }
}
