package br.com.vagaviva.scheduling.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.scheduling.application.port.in.ExpireConfirmationsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ConfirmationDeadlineJobTest {

    @Test
    @DisplayName("RF-27: o job de prazos só delega, com intervalo configurável e trava de cluster")
    void shouldDelegateWithClusterLock() throws Exception {
        ExpireConfirmationsUseCase expirations = mock(ExpireConfirmationsUseCase.class);

        new ConfirmationDeadlineJob(expirations).run();

        verify(expirations).expireOverdue();
        var run = ConfirmationDeadlineJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${vagaviva.scheduling.confirmation-deadline-check-interval}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("confirmation-deadline");
    }
}
