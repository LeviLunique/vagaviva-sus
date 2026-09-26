package br.com.vagaviva.engagement.adapter.in.web;

import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases;
import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases.NotificationFilter;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationStatus;
import br.com.vagaviva.engagement.domain.NotificationType;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notificações", description = "Log de entregas das mensagens ao paciente (RF-28)")
@RestController
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel ou unidade sem permissão", content = @Content(mediaType = "application/problem+json"))
class NotificationController {

    private final NotificationQueryUseCases queries;
    private final CurrentUserProvider currentUser;

    NotificationController(NotificationQueryUseCases queries, CurrentUserProvider currentUser) {
        this.queries = queries;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Log de notificações (ADMIN, SCHEDULER)",
            description = "Status, canal e tentativas — sem o texto nem o telefone. SCHEDULER informa um agendamento da própria unidade.")
    @ApiResponse(responseCode = "200", description = "Página de notificações, mais recentes primeiro")
    @GetMapping("/api/v1/notifications")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER')")
    PageResponse<NotificationResponse> list(@RequestParam(required = false) UUID appointmentId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(queries.list(new NotificationFilter(appointmentId, patientId), PageRequest.of(page, size),
                currentUser.get()), NotificationResponse::from);
    }

    record NotificationResponse(UUID id, UUID patientId, UUID appointmentId, UUID referralId, UUID offerId,
            NotificationType type, NotificationChannel channel, NotificationStatus status, int attempts,
            String lastError, Instant createdAt, Instant sentAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.id(), n.patientId(), n.appointmentId(), n.referralId(), n.offerId(),
                    n.type(), n.channel(), n.status(), n.attempts(), n.lastError(), n.createdAt(), n.sentAt());
        }
    }
}
