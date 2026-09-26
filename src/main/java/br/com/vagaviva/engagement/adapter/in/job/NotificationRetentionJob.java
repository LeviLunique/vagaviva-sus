package br.com.vagaviva.engagement.adapter.in.job;

import br.com.vagaviva.engagement.application.port.in.PurgeNotificationBodiesUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção diária do texto das mensagens (uma instância por vez). */
@Component
class NotificationRetentionJob {

    private final PurgeNotificationBodiesUseCase retention;

    NotificationRetentionJob(PurgeNotificationBodiesUseCase retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${vagaviva.retention.cron}", zone = "${vagaviva.time-zone}")
    @SchedulerLock(name = "notification-retention", lockAtMostFor = "PT30M")
    void run() {
        retention.purge();
    }
}
