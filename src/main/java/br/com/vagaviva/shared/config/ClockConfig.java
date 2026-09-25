package br.com.vagaviva.shared.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação, no fuso de negócio ({@code vagaviva.time-zone}). Instantes são
 * independentes de fuso (gravados em UTC); datas civis — idade do paciente, "D-3", "no dia" —
 * usam o fuso do relógio via {@code LocalDate.now(clock)}. Regras de prazo recebem
 * {@link Clock} por injeção para serem testáveis com {@code Clock.fixed(...)}.
 *
 * <p>O relógio avança em microssegundos — a precisão do {@code timestamptz} do PostgreSQL. Assim o
 * horário devolvido por uma escrita é idêntico ao lido depois (no Linux, {@code Instant.now()} tem
 * nanossegundos e o banco os descartaria).
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${vagaviva.time-zone:America/Sao_Paulo}") String timeZone) {
        return Clock.tick(Clock.system(ZoneId.of(timeZone)), Duration.ofNanos(1_000));
    }
}
