package br.com.vagaviva.scheduling.adapter.in.job;

import br.com.vagaviva.scheduling.application.port.in.ExpireConfirmationsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RF-27: expira as confirmações vencidas periodicamente (uma instância por vez). */
@Component
class ConfirmationDeadlineJob {

    private final ExpireConfirmationsUseCase expirations;

    ConfirmationDeadlineJob(ExpireConfirmationsUseCase expirations) {
        this.expirations = expirations;
    }

    @Scheduled(fixedDelayString = "${vagaviva.scheduling.confirmation-deadline-check-interval}",
            initialDelayString = "${vagaviva.scheduling.confirmation-deadline-initial-delay:PT1M}")
    @SchedulerLock(name = "confirmation-deadline", lockAtMostFor = "PT4M")
    void run() {
        expirations.expireOverdue();
    }
}
