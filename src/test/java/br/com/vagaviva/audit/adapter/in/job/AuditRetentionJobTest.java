package br.com.vagaviva.audit.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.audit.application.port.in.PurgeExpiredAuditUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AuditRetentionJobTest {

    @Test
    @DisplayName("retenção diária no horário configurado, com trava de cluster")
    void shouldPurgeDaily() throws Exception {
        PurgeExpiredAuditUseCase retention = mock(PurgeExpiredAuditUseCase.class);

        new AuditRetentionJob(retention).run();

        verify(retention).purge();
        var run = AuditRetentionJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).cron()).isEqualTo("${vagaviva.retention.cron}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("audit-retention");
    }
}
