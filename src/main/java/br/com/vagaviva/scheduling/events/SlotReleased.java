package br.com.vagaviva.scheduling.events;

import br.com.vagaviva.scheduling.SlotStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Vaga liberada por cancelamento, desistência ou prazo vencido; {@code outcome} diz para onde ela foi
 * (RN-15). {@code releaseCount} numera as liberações da mesma vaga (chave estável para as projeções).
 */
public record SlotReleased(UUID slotId, UUID unitId, UUID specialtyId, Reason reason, SlotStatus outcome,
        Instant startAt, int releaseCount, Instant releasedAt) {

    public enum Reason {
        PATIENT_CANCELLED,
        WITHDRAWN,
        CONFIRMATION_EXPIRED
    }
}
