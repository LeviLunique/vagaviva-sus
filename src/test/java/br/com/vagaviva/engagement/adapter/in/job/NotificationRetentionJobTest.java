package br.com.vagaviva.engagement.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.engagement.application.port.in.PurgeNotificationBodiesUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class NotificationRetentionJobTest {

    @Test
    @DisplayName("retenção diária do texto das mensagens, com trava de cluster")
    void shouldPurgeDaily() throws Exception {
        PurgeNotificationBodiesUseCase retention = mock(PurgeNotificationBodiesUseCase.class);

        new NotificationRetentionJob(retention).run();

        verify(retention).purge();
        var run = NotificationRetentionJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).cron()).isEqualTo("${vagaviva.retention.cron}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("notification-retention");
    }
}
