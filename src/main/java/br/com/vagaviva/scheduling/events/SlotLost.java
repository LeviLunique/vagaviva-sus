package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

/** Vaga liberada a menos de 2 h do início: não há tempo para reaproveitar (RN-15, indicador de perda). */
public record SlotLost(UUID slotId, UUID unitId, UUID specialtyId, Instant startAt) {
}
