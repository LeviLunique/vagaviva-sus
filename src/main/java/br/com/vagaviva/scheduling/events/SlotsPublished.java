package br.com.vagaviva.scheduling.events;

import java.util.UUID;

/** Uma unidade publicou vagas: dispara a alocação logo após o commit (RF-20). */
public record SlotsPublished(UUID unitId, UUID specialtyId, int count) {
}
