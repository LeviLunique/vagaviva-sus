package br.com.vagaviva.regulation.adapter.in.job;

import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-07: recalcula o snapshot da fila periodicamente — uma única instância por vez (ShedLock). */
@Component
class QueueSnapshotJob {

    private final QueueSnapshotUseCase snapshot;

    QueueSnapshotJob(QueueSnapshotUseCase snapshot) {
        this.snapshot = snapshot;
    }

    @Scheduled(fixedDelayString = "${vagaviva.regulation.queue-snapshot-interval}",
            initialDelayString = "${vagaviva.regulation.queue-snapshot-initial-delay:PT30S}")
    @SchedulerLock(name = "queue-snapshot", lockAtMostFor = "PT4M")
    void run() {
        snapshot.refresh();
    }
}
