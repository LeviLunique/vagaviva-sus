package br.com.vagaviva.insights.domain;

import org.jspecify.annotations.Nullable;

/** Contagens brutas de um recorte (período, especialidade, unidade), lidas das tabelas de fatos. */
public record PeriodCounts(long scheduled, long confirmed, long expiredUnconfirmed, long cancelled, long attended,
        long noShows, @Nullable Double avgWaitingDays, long released, long reallocated, long lost,
        @Nullable Double medianReoccupationHours) {

    public static PeriodCounts empty() {
        return new PeriodCounts(0, 0, 0, 0, 0, 0, null, 0, 0, 0, null);
    }
}
