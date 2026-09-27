package br.com.vagaviva.reallocation.events;

import java.time.Instant;
import java.util.UUID;

/** Encaixe aceito: vaga reaproveitada (indicador de reaproveitamento, F7). */
public record SlotOfferAccepted(UUID offerId, UUID slotId, UUID referralId, UUID patientId, UUID appointmentId,
        int round, Instant startAt) {
}
