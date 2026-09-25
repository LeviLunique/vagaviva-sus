package br.com.vagaviva.regulation.application.port.out;

import br.com.vagaviva.regulation.RiskClass;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model da transparência (RN-07): posições e estatísticas por especialidade. */
public interface QueueSnapshotRepository {

    /**
     * Recalcula tudo numa transação ({@code DELETE} + {@code INSERT ... SELECT}); quem lê durante o
     * recálculo continua vendo o snapshot anterior.
     *
     * @return quantos encaminhamentos estão na fila
     */
    int rebuild(Instant snapshotAt, Instant throughputSince, int throughputWindowDays);

    Optional<QueuePositionSnapshot> findPosition(UUID referralId);

    Optional<SpecialtyQueueStats> findStats(UUID specialtyId);

    List<SpecialtyQueueStats> findAllStats();

    record QueuePositionSnapshot(UUID referralId, UUID specialtyId, int position, int totalInQueue, RiskClass riskClass,
            Instant snapshotAt) {
    }

    record SpecialtyQueueStats(UUID specialtyId, int waitingRed, int waitingYellow, int waitingGreen, int waitingBlue,
            @Nullable BigDecimal avgWaitDays, @Nullable BigDecimal throughputPerDay, Instant snapshotAt) {

        public int totalWaiting() {
            return waitingRed + waitingYellow + waitingGreen + waitingBlue;
        }
    }
}
