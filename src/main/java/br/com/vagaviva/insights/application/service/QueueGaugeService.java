package br.com.vagaviva.insights.application.service;

import br.com.vagaviva.insights.application.port.in.RefreshQueueGaugeUseCase;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.WaitingCount;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** RF-36: {@code vagaviva.queue.waiting{specialty}} — pacientes aguardando por especialidade. */
@Service
class QueueGaugeService implements RefreshQueueGaugeUseCase {

    private final QueueApi queue;
    private final MultiGauge waiting;

    QueueGaugeService(QueueApi queue, MeterRegistry meters) {
        this.queue = queue;
        this.waiting = MultiGauge.builder("vagaviva.queue.waiting")
                .description("Pacientes aguardando na fila, por especialidade").register(meters);
    }

    @Override
    public void refresh() {
        Map<UUID, Long> bySpecialty = queue.waitingCounts().stream()
                .collect(Collectors.groupingBy(WaitingCount::specialtyId, Collectors.summingLong(WaitingCount::waiting)));
        waiting.register(bySpecialty.entrySet().stream()
                .map(entry -> MultiGauge.Row.of(Tags.of("specialty", entry.getKey().toString()), entry.getValue()))
                .toList(), true);
    }
}
