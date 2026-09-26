package br.com.vagaviva.insights.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.insights.application.port.in.RefreshQueueGaugeUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class QueueGaugeJobTest {

    @Test
    @DisplayName("o job só atualiza o gauge, no intervalo configurado")
    void shouldRefreshGauge() throws Exception {
        RefreshQueueGaugeUseCase gauge = mock(RefreshQueueGaugeUseCase.class);

        new QueueGaugeJob(gauge).run();

        verify(gauge).refresh();
        assertThat(QueueGaugeJob.class.getDeclaredMethod("run").getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${vagaviva.insights.queue-gauge-interval}");
    }
}
