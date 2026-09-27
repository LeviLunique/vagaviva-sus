package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

/** Vaga com início entre 2 h e 5 dias: vai para o encaixe em cascata (F6). */
public record SlotOpenedForOffers(UUID slotId, UUID unitId, UUID specialtyId, Instant startAt) {
}
