package br.com.vagaviva.engagement.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.engagement.application.port.in.NotifyPatientUseCases;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ReminderJobTest {

    @Test
    @DisplayName("RN-11: o job de lembretes só delega, com intervalo configurável e trava de cluster")
    void shouldDelegateWithClusterLock() throws Exception {
        NotifyPatientUseCases notify = mock(NotifyPatientUseCases.class);

        new ReminderJob(notify).run();

        verify(notify).sendDueReminders();
        var run = ReminderJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString()).isEqualTo("${vagaviva.engagement.reminder-interval}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("reminders");
    }
}
