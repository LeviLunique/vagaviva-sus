package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.scheduling.domain.ConfirmationPolicy;
import br.com.vagaviva.scheduling.domain.ReleasePolicy;
import br.com.vagaviva.scheduling.domain.SlotLeadTimes;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Regras de domínio configuráveis montadas a partir de {@link SchedulingProperties}. */
@Configuration
class SchedulingPolicies {

    @Bean
    SlotLeadTimes slotLeadTimes(SchedulingProperties properties) {
        return new SlotLeadTimes(properties.regularAllocationMinLead(), properties.shortNoticeMinLead());
    }

    @Bean
    ReleasePolicy releasePolicy(SlotLeadTimes slotLeadTimes) {
        return new ReleasePolicy(slotLeadTimes);
    }

    /** O prazo usa o fuso do relógio de negócio (RN-11). */
    @Bean
    ConfirmationPolicy confirmationPolicy(SchedulingProperties properties, Clock clock) {
        return new ConfirmationPolicy((int) properties.confirmationDeadlineBeforeStart().toDays(), clock.getZone());
    }
}
