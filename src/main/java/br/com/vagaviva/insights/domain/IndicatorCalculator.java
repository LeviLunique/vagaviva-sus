package br.com.vagaviva.insights.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Fórmulas dos indicadores (PNR-SUS art. 56, VI e VII) — sem divisão por zero, arredondamento explícito. */
public final class IndicatorCalculator {

    private IndicatorCalculator() {
    }

    /** Fração 0–1 com 4 casas; {@code null} quando não há denominador. */
    public static @Nullable BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    /** Médias e medianas com 1 casa; {@code null} sem amostra. */
    public static @Nullable BigDecimal oneDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    /** absenteísmo = faltas ÷ (atendidos + faltas). */
    public static @Nullable BigDecimal absenteeismRate(PeriodCounts counts) {
        return rate(counts.noShows(), counts.attended() + counts.noShows());
    }

    /** confirmação = confirmados ÷ agendamentos cujo prazo terminou (confirmados + expirados sem confirmação). */
    public static @Nullable BigDecimal confirmationRate(PeriodCounts counts) {
        return rate(counts.confirmed(), counts.confirmed() + counts.expiredUnconfirmed());
    }

    /** reaproveitamento = vagas liberadas que voltaram a ser ocupadas ÷ vagas liberadas. */
    public static @Nullable BigDecimal reuseRate(PeriodCounts counts) {
        return rate(counts.reallocated(), counts.released());
    }

    /** Σ mensagens enviadas × custo do canal, em reais (2 casas); canal sem preço custa zero. */
    public static BigDecimal messagingCost(Map<String, Long> sentByChannel, Map<String, BigDecimal> pricePerMessage) {
        BigDecimal total = BigDecimal.ZERO;
        for (var sent : sentByChannel.entrySet()) {
            total = total.add(pricePerMessage.getOrDefault(sent.getKey(), BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(sent.getValue())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
