package br.com.vagaviva.engagement.application.port.in;

import br.com.vagaviva.scheduling.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RF-26/RF-32: ações do paciente pelo link, sem login — o token é a credencial. */
public interface PatientActionUseCases {

    /**
     * Agendamento ({@code kind = APPOINTMENT}) ou oferta de encaixe ({@code kind = OFFER}).
     *
     * @throws br.com.vagaviva.shared.domain.NotFoundException token inexistente
     * @throws br.com.vagaviva.shared.domain.GoneException token expirado
     */
    PatientLinkView view(String token, @Nullable String clientIp);

    ActionResult confirm(String token, @Nullable String clientIp);

    ActionResult cancel(String token, @Nullable String clientIp);

    ActionResult withdraw(String token, @Nullable String clientIp);

    /** RF-32: o primeiro aceite fica com a vaga; os demais recebem {@code 409 SLOT_ALREADY_FILLED}. */
    OfferActionResult acceptOffer(String token, @Nullable String clientIp);

    OfferActionResult declineOffer(String token, @Nullable String clientIp);

    enum PatientAction {
        CONFIRM,
        CANCEL,
        WITHDRAW,
        ACCEPT_OFFER,
        DECLINE_OFFER
    }

    /**
     * O que o paciente vê — mínimo (RN-20): primeiro nome, rótulo genérico, data, unidade. Agendamento
     * traz o prazo de confirmação; oferta, o prazo de resposta ({@code expiresAt}).
     */
    record PatientLinkView(String kind, String firstName, String label, Instant startAt, String unitName,
            String unitAddress, String status, @Nullable Instant confirmationDeadline, @Nullable Instant expiresAt,
            List<PatientAction> allowedActions) {
    }

    record ActionResult(AppointmentStatus status, boolean backToQueue) {
    }

    record OfferActionResult(String status, @Nullable UUID appointmentId) {
    }
}
