package br.com.vagaviva.reallocation.adapter.in.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.scheduling.events.SlotOpenedForOffers;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotOpenedForOffersListenerTest {

    @Test
    @DisplayName("vaga aberta para encaixe inicia a cascata de ofertas")
    void shouldStartCampaign() {
        StartOfferCampaignUseCase campaign = mock(StartOfferCampaignUseCase.class);
        UUID slot = UUID.randomUUID();

        new SlotOpenedForOffersListener(campaign).on(new SlotOpenedForOffers(slot, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-27T14:00:00Z")));

        verify(campaign).advance(slot);
    }
}
