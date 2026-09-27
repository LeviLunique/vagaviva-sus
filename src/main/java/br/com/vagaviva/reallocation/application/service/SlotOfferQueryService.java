package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SlotView;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SlotOfferQueryService implements SlotOfferQueryUseCase {

    private final SlotOfferRepository offers;
    private final SchedulingApi scheduling;

    SlotOfferQueryService(SlotOfferRepository offers, SchedulingApi scheduling) {
        this.offers = offers;
        this.scheduling = scheduling;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SlotOffer> list(OfferFilter filter, Pageable pageable, CurrentUser actor) {
        if (actor.role() == Role.SCHEDULER) {
            boolean ownSlot = filter.slotId() != null && scheduling.findSlotView(filter.slotId())
                    .map(SlotView::unitId).filter(unit -> unit.equals(actor.unitId())).isPresent();
            if (!ownSlot) {
                throw ReallocationErrors.outOfUnit();
            }
        }
        return offers.search(filter, pageable);
    }
}
