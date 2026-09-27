package br.com.vagaviva.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    @DisplayName("o relógio da aplicação usa o fuso de negócio para datas civis")
    void shouldUseBusinessTimeZone() {
        assertThat(new ClockConfig().clock("America/Sao_Paulo").getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    @DisplayName("o relógio tem a precisão do PostgreSQL (microssegundos), sem nanossegundos residuais")
    void shouldTickInMicroseconds() {
        var clock = new ClockConfig().clock("America/Sao_Paulo");

        for (int i = 0; i < 1_000; i++) {
            assertThat(clock.instant().getNano() % 1_000).isZero();
        }
    }
}
