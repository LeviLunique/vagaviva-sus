package br.com.vagaviva.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async}, usado pelos {@code @ApplicationModuleListener} do Spring Modulith: o
 * listener roda depois do commit, em outra thread (virtual) e transação própria.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
