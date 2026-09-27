package br.com.vagaviva.reallocation.application.port.in;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** RF-33: rodadas, status e respostas das ofertas. */
public interface SlotOfferQueryUseCase {

    /** SCHEDULER precisa informar uma vaga da própria unidade (RN-03). */
    Page<SlotOffer> list(OfferFilter filter, Pageable pageable, CurrentUser actor);

    record OfferFilter(@Nullable UUID slotId, @Nullable OfferStatus status) {
    }
}
