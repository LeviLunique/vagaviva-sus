package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.reallocation.domain.OfferRoundPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReallocationBeans {

    @Bean
    OfferRoundPolicy offerRoundPolicy(ReallocationProperties properties) {
        return new OfferRoundPolicy(properties.offerBatchSize(), properties.offerMaxRounds(),
                properties.offerResponseWindow(), properties.shortNoticeMinLead());
    }
}
