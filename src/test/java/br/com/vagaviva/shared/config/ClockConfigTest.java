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
}
