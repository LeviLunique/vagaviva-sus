package br.com.vagaviva.reallocation;

import java.time.Instant;
import java.util.UUID;

/** Oferta com os dados da vaga, para o link do paciente. */
public record OfferView(UUID id, UUID slotId, UUID referralId, UUID patientId, UUID unitId, UUID specialtyId,
        Instant startAt, Instant expiresAt, int round, OfferStatus status) {
}
