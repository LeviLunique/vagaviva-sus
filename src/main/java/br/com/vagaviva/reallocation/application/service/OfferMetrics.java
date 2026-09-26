package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.reallocation.OfferStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** RF-36: {@code vagaviva.offers{status}} — cada transição de oferta conta uma vez no status de destino. */
@Component
class OfferMetrics {

    private final MeterRegistry meters;

    OfferMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    void count(OfferStatus status, int amount) {
        if (amount > 0) {
            meters.counter("vagaviva.offers", "status", status.name()).increment(amount);
        }
    }
}
