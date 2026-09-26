package br.com.vagaviva.reallocation;

import java.util.Optional;
import java.util.UUID;

/** API do encaixe para o engajamento (ações do paciente pelo link). */
public interface ReallocationApi {

    Optional<OfferView> findOfferView(UUID offerId);

    /**
     * RF-32/RN-17: o primeiro aceite válido fica com a vaga (agendamento já confirmado).
     *
     * @throws br.com.vagaviva.shared.domain.ConflictException {@code SLOT_ALREADY_FILLED} ou {@code OFFER_NOT_PENDING}
     * @throws br.com.vagaviva.shared.domain.GoneException {@code OFFER_EXPIRED}
     */
    AcceptedOffer accept(UUID offerId);

    /** RF-32: recusa não tira o paciente da fila; repetir é aceito. Rodada encerrada ⇒ próxima. */
    OfferView decline(UUID offerId);

    record AcceptedOffer(UUID offerId, UUID appointmentId) {
    }
}
