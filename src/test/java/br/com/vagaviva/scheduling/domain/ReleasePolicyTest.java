package br.com.vagaviva.scheduling.domain;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.LEAD_TIMES;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.scheduling.SlotStatus;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

class ReleasePolicyTest {

    private final ReleasePolicy policy = new ReleasePolicy(LEAD_TIMES);

    @ParameterizedTest(name = "início em {0} min ⇒ {1}")
    @CsvSource({
        // 5 dias exatos (7200 min) ou mais ⇒ volta à alocação regular
        "7200, AVAILABLE",
        "20000, AVAILABLE",
        // um minuto a menos ⇒ encaixe
        "7199, OPEN_FOR_OFFERS",
        // 2 h exatas ainda dá para ofertar
        "120, OPEN_FOR_OFFERS",
        // menos de 2 h ⇒ perdida
        "119, EXPIRED",
        "0, EXPIRED"
    })
    @DisplayName("RN-15: limites de 5 dias e 2 h com relógio fixo")
    void shouldDecideByLeadTime(long minutesAhead, SlotStatus expected) {
        assertThat(policy.decide(NOW.plus(Duration.ofMinutes(minutesAhead)), NOW)).isEqualTo(expected);
    }
}
