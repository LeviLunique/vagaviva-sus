package br.com.vagaviva.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async}, usado pelos {@code @ApplicationModuleListener} do Spring Modulith: o
 * listener roda depois do commit, em outra thread (virtual) e transação própria.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * RNF-06: o listener herda o contexto de trace de quem publicou o evento — o mesmo {@code traceId}
     * aparece nos logs da requisição e do processamento assíncrono (e no X-Ray).
     */
    @Bean
    TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
