package br.com.vagaviva.reallocation.events;

import java.time.Instant;
import java.util.UUID;

/** Oferta de encaixe criada: o engajamento envia a mensagem com o link de aceite. */
public record SlotOffered(UUID offerId, UUID slotId, UUID patientId, UUID referralId, Instant startAt,
        Instant expiresAt) {
}
