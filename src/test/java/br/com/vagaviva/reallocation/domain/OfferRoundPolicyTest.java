package br.com.vagaviva.reallocation.domain;

import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.NOW;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OfferRoundPolicyTest {

    @Test
    @DisplayName("RN-17: prazo = janela de 4 h quando a vaga está longe")
    void shouldUseResponseWindow() {
        Instant start = NOW.plus(Duration.ofHours(26));

        assertThat(POLICY.expiresAt(start, NOW)).isEqualTo(NOW.plus(Duration.ofHours(4)));
    }

    @Test
    @DisplayName("RN-17: prazo = início − 2 h quando a vaga está perto (mín das duas regras)")
    void shouldCapAtStartMinusLead() {
        Instant start = NOW.plus(Duration.ofHours(5));

        assertThat(POLICY.expiresAt(start, NOW)).isEqualTo(NOW.plus(Duration.ofHours(3)));
    }

    @Test
    @DisplayName("RN-16: até 5 rodadas, e só enquanto faltar mais que 2 h para o início")
    void shouldLimitRounds() {
        Instant start = NOW.plus(Duration.ofHours(26));

        assertThat(POLICY.canOpenRound(1, start, NOW)).isTrue();
        assertThat(POLICY.canOpenRound(5, start, NOW)).isTrue();
        assertThat(POLICY.canOpenRound(6, start, NOW)).isFalse();
        assertThat(POLICY.canOpenRound(1, NOW.plus(Duration.ofHours(2)), NOW)).isFalse();
        assertThat(POLICY.canOpenRound(1, NOW.plus(Duration.ofHours(2)).plusSeconds(1), NOW)).isTrue();
    }

    @Test
    @DisplayName("configuração inválida é recusada")
    void shouldValidateConfiguration() {
        assertThatThrownBy(() -> new OfferRoundPolicy(0, 5, Duration.ofHours(4), Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferRoundPolicy(3, 0, Duration.ofHours(4), Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
