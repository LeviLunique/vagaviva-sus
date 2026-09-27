package br.com.vagaviva.reallocation.adapter.in.job;

import br.com.vagaviva.reallocation.application.port.in.ExpireOffersUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-17: expira ofertas sem resposta e avança a cascata (uma instância por vez). */
@Component
class OfferExpirationJob {

    private final ExpireOffersUseCase expirations;

    OfferExpirationJob(ExpireOffersUseCase expirations) {
        this.expirations = expirations;
    }

    @Scheduled(fixedDelayString = "${vagaviva.reallocation.offer-expiration-check-interval}",
            initialDelayString = "${vagaviva.reallocation.offer-expiration-initial-delay:PT1M}")
    @SchedulerLock(name = "offer-expiration", lockAtMostFor = "PT50S")
    void run() {
        expirations.expireOverdue();
    }
}
