package br.com.vagaviva.reallocation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * RN-16/RN-17: lotes de {@code batchSize} pacientes, até {@code maxRounds} rodadas, cada oferta com
 * prazo de resposta = mín(janela, início − antecedência mínima do encaixe).
 */
public record OfferRoundPolicy(int batchSize, int maxRounds, Duration responseWindow, Duration minLead) {

    public OfferRoundPolicy {
        if (batchSize < 1 || maxRounds < 1) {
            throw new IllegalArgumentException("Lote e rodadas devem ser positivos.");
        }
        Objects.requireNonNull(responseWindow, "responseWindow");
        Objects.requireNonNull(minLead, "minLead");
    }

    /** Ainda cabe uma rodada? Número dentro do limite e prazo final do encaixe no futuro. */
    public boolean canOpenRound(int round, Instant startAt, Instant now) {
        return round <= maxRounds && lastResponseAt(startAt).isAfter(now);
    }

    public Instant expiresAt(Instant startAt, Instant now) {
        Instant byWindow = now.plus(responseWindow);
        Instant last = lastResponseAt(startAt);
        return byWindow.isBefore(last) ? byWindow : last;
    }

    private Instant lastResponseAt(Instant startAt) {
        return startAt.minus(minLead);
    }
}
