package br.com.vagaviva.insights.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndicatorCalculatorTest {

    private static PeriodCounts counts(long confirmed, long expired, long attended, long noShows, long released,
            long reallocated) {
        return new PeriodCounts(0, confirmed, expired, 0, attended, noShows, null, released, reallocated, 0, null);
    }

    @Test
    @DisplayName("sem denominador a taxa é null (nunca divisão por zero)")
    void rateWithoutDenominatorIsNull() {
        PeriodCounts empty = PeriodCounts.empty();

        assertThat(IndicatorCalculator.absenteeismRate(empty)).isNull();
        assertThat(IndicatorCalculator.confirmationRate(empty)).isNull();
        assertThat(IndicatorCalculator.reuseRate(empty)).isNull();
        assertThat(IndicatorCalculator.rate(3, -1)).isNull();
    }

    @Test
    @DisplayName("PNR-SUS art. 56, VII: absenteísmo = faltas ÷ (atendidos + faltas), 4 casas HALF_UP")
    void absenteeismRounding() {
        assertThat(IndicatorCalculator.absenteeismRate(counts(0, 0, 2, 1, 0, 0))).isEqualByComparingTo("0.3333");
        assertThat(IndicatorCalculator.absenteeismRate(counts(0, 0, 1, 2, 0, 0))).isEqualByComparingTo("0.6667");
        assertThat(IndicatorCalculator.absenteeismRate(counts(0, 0, 0, 4, 0, 0))).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("confirmação sobre prazos encerrados; reaproveitamento sobre vagas liberadas")
    void confirmationAndReuse() {
        PeriodCounts c = counts(3, 1, 0, 0, 8, 6);

        assertThat(IndicatorCalculator.confirmationRate(c)).isEqualByComparingTo("0.7500");
        assertThat(IndicatorCalculator.reuseRate(c)).isEqualByComparingTo("0.7500");
    }

    @Test
    @DisplayName("médias com 1 casa; custo de mensagens por canal com 2 casas, canal sem preço custa zero")
    void averagesAndCost() {
        assertThat(IndicatorCalculator.oneDecimal(12.345)).isEqualByComparingTo("12.3");
        assertThat(IndicatorCalculator.oneDecimal(12.35)).isEqualByComparingTo("12.4");
        assertThat(IndicatorCalculator.oneDecimal(null)).isNull();

        BigDecimal cost = IndicatorCalculator.messagingCost(Map.of("SMS", 10L, "WHATSAPP", 3L, "SANDBOX", 50L, "PUSH", 2L),
                Map.of("SMS", new BigDecimal("0.16"), "WHATSAPP", new BigDecimal("0.05"), "SANDBOX", BigDecimal.ZERO));

        assertThat(cost).isEqualByComparingTo("1.75").hasScaleOf(2);
    }
}
