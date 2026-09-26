package br.com.vagaviva.reallocation.application.port.in;

import java.util.UUID;

/** RF-31: vaga aberta para encaixe ⇒ próxima rodada de ofertas (ou vaga perdida, se não couber mais nenhuma). */
public interface StartOfferCampaignUseCase {

    void advance(UUID slotId);
}
