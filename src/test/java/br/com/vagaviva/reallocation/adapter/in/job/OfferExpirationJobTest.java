package br.com.vagaviva.reallocation.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.reallocation.application.port.in.ExpireOffersUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class OfferExpirationJobTest {

    @Test
    @DisplayName("RN-17: o job de expiração só delega, com intervalo configurável e trava de cluster")
    void shouldDelegateWithClusterLock() throws Exception {
        ExpireOffersUseCase expirations = mock(ExpireOffersUseCase.class);

        new OfferExpirationJob(expirations).run();

        verify(expirations).expireOverdue();
        var run = OfferExpirationJob.class.getDeclaredMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${vagaviva.reallocation.offer-expiration-check-interval}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("offer-expiration");
    }
}
