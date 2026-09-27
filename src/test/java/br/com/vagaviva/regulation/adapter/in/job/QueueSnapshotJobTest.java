package br.com.vagaviva.regulation.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class QueueSnapshotJobTest {

    @Test
    @DisplayName("o job só delega ao caso de uso, com agendamento configurável e trava de cluster (ShedLock)")
    void shouldDelegateWithClusterLock() throws Exception {
        QueueSnapshotUseCase useCase = mock(QueueSnapshotUseCase.class);

        new QueueSnapshotJob(useCase).run();

        verify(useCase).refresh();
        var run = QueueSnapshotJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${vagaviva.regulation.queue-snapshot-interval}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("queue-snapshot");
    }
}
