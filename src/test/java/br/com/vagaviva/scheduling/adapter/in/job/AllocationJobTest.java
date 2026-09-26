package br.com.vagaviva.scheduling.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AllocationJobTest {

    @Test
    @DisplayName("o job só delega ao motor de alocação, com intervalo configurável e trava de cluster")
    void shouldDelegateWithClusterLock() throws Exception {
        RunAllocationUseCase allocation = mock(RunAllocationUseCase.class);

        new AllocationJob(allocation).run();

        verify(allocation).run();
        var run = AllocationJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString()).isEqualTo("${vagaviva.scheduling.allocation-interval}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("allocation");
    }
}
