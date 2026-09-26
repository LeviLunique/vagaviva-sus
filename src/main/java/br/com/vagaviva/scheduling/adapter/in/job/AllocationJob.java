package br.com.vagaviva.scheduling.adapter.in.job;

import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RF-20: alocação periódica. A concorrência já é segura por {@code SKIP LOCKED}; o ShedLock só
 * evita que várias instâncias façam o mesmo trabalho à toa.
 */
@Component
class AllocationJob {

    private final RunAllocationUseCase allocation;

    AllocationJob(RunAllocationUseCase allocation) {
        this.allocation = allocation;
    }

    @Scheduled(fixedDelayString = "${vagaviva.scheduling.allocation-interval}",
            initialDelayString = "${vagaviva.scheduling.allocation-initial-delay:PT1M}")
    @SchedulerLock(name = "allocation", lockAtMostFor = "PT4M")
    void run() {
        allocation.run();
    }
}
