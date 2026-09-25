package br.com.vagaviva.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação. Regras de prazo (confirmação, expiração de ofertas) recebem
 * {@link Clock} por injeção para serem testáveis com {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
