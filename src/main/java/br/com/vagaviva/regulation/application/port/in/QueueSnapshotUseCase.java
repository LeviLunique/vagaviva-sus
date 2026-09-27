package br.com.vagaviva.regulation.application.port.in;

import java.time.Instant;

/** RN-07: recálculo do snapshot da fila (job a cada 5 min ou sob demanda pelo ADMIN). */
public interface QueueSnapshotUseCase {

    SnapshotResult refresh();

    record SnapshotResult(Instant snapshotAt, int queued) {
    }
}
