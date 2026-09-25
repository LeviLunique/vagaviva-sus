package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class QueueSnapshotService implements QueueSnapshotUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueueSnapshotService.class);

    private final QueueSnapshotRepository snapshots;
    private final RegulationProperties properties;
    private final Clock clock;

    QueueSnapshotService(QueueSnapshotRepository snapshots, RegulationProperties properties, Clock clock) {
        this.snapshots = snapshots;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SnapshotResult refresh() {
        // Precisão do timestamptz (µs): o horário devolvido é idêntico ao gravado e lido depois.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int windowDays = (int) Math.max(1, properties.throughputWindow().toDays());
        int queued = snapshots.rebuild(now, now.minus(properties.throughputWindow()), windowDays);
        log.info("Snapshot da fila recalculado: {} encaminhamento(s) aguardando.", queued);
        return new SnapshotResult(now, queued);
    }
}
