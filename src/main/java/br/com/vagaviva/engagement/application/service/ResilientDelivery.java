package br.com.vagaviva.engagement.application.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Proteção da chamada ao provedor (instância {@code notification-channel}): retentativas curtas
 * com backoff e circuit breaker — com o provedor fora do ar, as mensagens falham rápido e ficam na
 * fila para reentrega, sem prender threads (a API não é afetada — RF-24 CA1).
 */
@Component
class ResilientDelivery {

    static final String INSTANCE = "notification-channel";

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    ResilientDelivery(RetryRegistry retries, CircuitBreakerRegistry circuitBreakers) {
        this.retry = retries.retry(INSTANCE);
        this.circuitBreaker = circuitBreakers.circuitBreaker(INSTANCE);
    }

    <T> T call(Supplier<T> delivery) {
        // Retry por fora do circuit breaker: com o circuito aberto, as tentativas falham na hora.
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, delivery)).get();
    }
}
