package br.com.vagaviva.scheduling.events;

import br.com.vagaviva.scheduling.SlotStatus;
import java.time.Instant;
import java.util.UUID;

/** Vaga liberada por cancelamento, desistência ou prazo vencido; {@code outcome} diz para onde ela foi (RN-15). */
public record SlotReleased(UUID slotId, Reason reason, SlotStatus outcome, Instant startAt) {

    public enum Reason {
        PATIENT_CANCELLED,
        WITHDRAWN,
        CONFIRMATION_EXPIRED
    }
}
