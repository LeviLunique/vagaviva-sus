package br.com.vagaviva.insights.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Escrita idempotente dos fatos: cada método devolve {@code true} só na primeira vez que muda a linha
 * (reprocessar o mesmo evento não altera nada e não conta métrica de novo).
 */
public interface FactStore {

    boolean insertAppointment(UUID appointmentId, UUID specialtyId, UUID unitId, String origin, Instant scheduledAt,
            Instant startAt, @Nullable Instant queueEnteredAt, boolean confirmed, @Nullable BigDecimal waitingDays);

    boolean markConfirmed(UUID appointmentId);

    boolean closeAppointment(UUID appointmentId, String finalStatus, Instant outcomeAt);

    boolean insertRelease(UUID id, UUID slotId, UUID specialtyId, UUID unitId, String reason, Instant releasedAt,
            @Nullable String outcome);

    /** Fecha a liberação em aberto da vaga com o desfecho (REALLOCATED, OFFER_ACCEPTED, LOST). */
    boolean closeRelease(UUID slotId, String outcome, Instant outcomeAt);
}
