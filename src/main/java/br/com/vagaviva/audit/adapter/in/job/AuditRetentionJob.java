package br.com.vagaviva.audit.adapter.in.job;

import br.com.vagaviva.audit.application.port.in.PurgeExpiredAuditUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção diária da trilha de auditoria (uma instância por vez). */
@Component
class AuditRetentionJob {

    private final PurgeExpiredAuditUseCase retention;

    AuditRetentionJob(PurgeExpiredAuditUseCase retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${vagaviva.retention.cron}", zone = "${vagaviva.time-zone}")
    @SchedulerLock(name = "audit-retention", lockAtMostFor = "PT30M")
    void run() {
        retention.purge();
    }
}
