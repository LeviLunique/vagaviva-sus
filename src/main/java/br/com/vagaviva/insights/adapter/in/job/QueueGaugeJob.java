package br.com.vagaviva.insights.adapter.in.job;

import br.com.vagaviva.insights.application.port.in.RefreshQueueGaugeUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Atualiza o gauge da fila; roda em todas as instâncias (cada uma publica o próprio valor). */
@Component
class QueueGaugeJob {

    private final RefreshQueueGaugeUseCase gauge;

    QueueGaugeJob(RefreshQueueGaugeUseCase gauge) {
        this.gauge = gauge;
    }

    @Scheduled(fixedDelayString = "${vagaviva.insights.queue-gauge-interval}",
            initialDelayString = "${vagaviva.insights.queue-gauge-initial-delay:PT30S}")
    void run() {
        gauge.refresh();
    }
}
