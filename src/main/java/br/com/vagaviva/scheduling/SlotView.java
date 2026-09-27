package br.com.vagaviva.scheduling;

import java.time.Instant;
import java.util.UUID;

/** Visão da vaga para outros módulos (encaixe em cascata, F6). */
public record SlotView(UUID id, UUID unitId, UUID specialtyId, Instant startAt, SlotStatus status) {
}
