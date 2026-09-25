package br.com.vagaviva.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação, no fuso de negócio ({@code vagaviva.time-zone}). Instantes são
 * independentes de fuso (gravados em UTC); datas civis — idade do paciente, "D-3", "no dia" —
 * usam o fuso do relógio via {@code LocalDate.now(clock)}. Regras de prazo recebem
 * {@link Clock} por injeção para serem testáveis com {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${vagaviva.time-zone:America/Sao_Paulo}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
