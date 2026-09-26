package br.com.vagaviva.insights.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.WaitingCount;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueueGaugeServiceTest {

    @Test
    @DisplayName("RF-36: gauge vagaviva.queue.waiting soma as classes de risco por especialidade e some quem esvaziou")
    void shouldPublishQueueSizePerSpecialty() {
        QueueApi queue = mock(QueueApi.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        UUID cardio = UUID.randomUUID();
        UUID psiq = UUID.randomUUID();
        when(queue.waitingCounts()).thenReturn(List.of(new WaitingCount(cardio, RiskClass.RED, 2),
                new WaitingCount(cardio, RiskClass.BLUE, 3), new WaitingCount(psiq, RiskClass.GREEN, 1)),
                List.of(new WaitingCount(cardio, RiskClass.RED, 1)));
        var service = new QueueGaugeService(queue, meters);

        service.refresh();
        assertThat(meters.get("vagaviva.queue.waiting").tag("specialty", cardio.toString()).gauge().value()).isEqualTo(5.0);
        assertThat(meters.get("vagaviva.queue.waiting").tag("specialty", psiq.toString()).gauge().value()).isEqualTo(1.0);

        service.refresh();
        assertThat(meters.get("vagaviva.queue.waiting").tag("specialty", cardio.toString()).gauge().value()).isEqualTo(1.0);
        assertThat(meters.find("vagaviva.queue.waiting").tag("specialty", psiq.toString()).gauge()).isNull();
    }
}
