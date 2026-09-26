package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.engagement.domain.MessageComposer;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EngagementBeans {

    /** Datas e horas das mensagens no fuso de negócio. */
    @Bean
    MessageComposer messageComposer(Clock clock) {
        return new MessageComposer(clock.getZone());
    }
}
