package br.com.vagaviva.reallocation.adapter.in.event;

import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.scheduling.events.SlotOpenedForOffers;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Vaga publicada ou liberada com início entre 2 h e 5 dias ⇒ primeira rodada de ofertas (após o commit). */
@Component
class SlotOpenedForOffersListener {

    private final StartOfferCampaignUseCase campaign;

    SlotOpenedForOffersListener(StartOfferCampaignUseCase campaign) {
        this.campaign = campaign;
    }

    @ApplicationModuleListener
    void on(SlotOpenedForOffers event) {
        campaign.advance(event.slotId());
    }
}
