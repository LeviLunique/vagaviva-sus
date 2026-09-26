package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.reallocation.application.port.in.ExpireOffersUseCase;
import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** RN-17: expira as ofertas vencidas vaga a vaga (transação própria) e avança a cascata. */
@Service
class OfferExpirationService implements ExpireOffersUseCase {

    private static final Logger log = LoggerFactory.getLogger(OfferExpirationService.class);
    private static final int BATCH = 100;

    private final SlotOfferRepository offers;
    private final StartOfferCampaignUseCase campaign;
    private final TransactionTemplate perSlot;
    private final Clock clock;

    OfferExpirationService(SlotOfferRepository offers, StartOfferCampaignUseCase campaign,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.offers = offers;
        this.campaign = campaign;
        this.perSlot = new TransactionTemplate(transactionManager);
        this.perSlot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public int expireOverdue() {
        int processed = 0;
        for (UUID slotId : offers.findSlotsWithOverdueOffers(clock.instant(), BATCH)) {
            try {
                perSlot.executeWithoutResult(status -> {
                    offers.expireOverdue(slotId, clock.instant());
                    campaign.advance(slotId);
                });
                processed++;
            } catch (RuntimeException ex) {
                log.warn("Falha ao expirar as ofertas da vaga {}: {}", slotId, ex.getMessage());
            }
        }
        return processed;
    }
}
