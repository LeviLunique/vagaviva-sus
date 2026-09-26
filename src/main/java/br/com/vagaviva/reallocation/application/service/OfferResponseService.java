package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.reallocation.OfferView;
import br.com.vagaviva.reallocation.ReallocationApi;
import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SlotView;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respostas às ofertas (RF-32). A oferta é lida sem trava: o ponto de serialização entre aceites
 * concorrentes é o {@code UPDATE} condicional da vaga na agenda. Travar a oferta causaria deadlock —
 * cada perdedor seguraria a própria oferta esperando a vaga, e o vencedor seguraria a vaga esperando
 * para marcar as outras ofertas como {@code SUPERSEDED}.
 */
@Service
class OfferResponseService implements ReallocationApi {

    private final SlotOfferRepository offers;
    private final SchedulingApi scheduling;
    private final StartOfferCampaignUseCase campaign;
    private final ReallocationAudit audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OfferResponseService(SlotOfferRepository offers, SchedulingApi scheduling, StartOfferCampaignUseCase campaign,
            ReallocationAudit audit, ApplicationEventPublisher events, Clock clock) {
        this.offers = offers;
        this.scheduling = scheduling;
        this.campaign = campaign;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfferView> findOfferView(UUID offerId) {
        return offers.findById(offerId).flatMap(offer -> scheduling.findSlotView(offer.slotId())
                .map(slot -> view(offer, slot)));
    }

    @Override
    @Transactional
    public AcceptedOffer accept(UUID offerId) {
        SlotOffer offer = load(offerId);
        offer.accept(clock);
        AppointmentView appointment = scheduling.allocateFromOffer(offer.slotId(), offer.referralId());
        offers.save(offer);
        offers.supersedeOthers(offer.slotId(), offer.id(), clock.instant());
        events.publishEvent(new SlotOfferAccepted(offer.id(), offer.slotId(), offer.referralId(), offer.patientId(),
                appointment.id(), offer.round(), appointment.startAt()));
        audit.record(ReallocationAudit.OFFER_ACCEPTED, ReallocationAudit.SLOT_OFFER, offer.id(),
                Map.of("slotId", offer.slotId().toString(), "appointmentId", appointment.id().toString(),
                        "round", String.valueOf(offer.round())));
        return new AcceptedOffer(offer.id(), appointment.id());
    }

    @Override
    @Transactional
    public OfferView decline(UUID offerId) {
        SlotOffer offer = load(offerId);
        if (offer.decline(clock)) {
            offers.save(offer);
            audit.record(ReallocationAudit.OFFER_DECLINED, ReallocationAudit.SLOT_OFFER, offer.id(),
                    Map.of("slotId", offer.slotId().toString(), "round", String.valueOf(offer.round())));
            campaign.advance(offer.slotId());
        }
        return findOfferView(offerId).orElseThrow(ReallocationErrors::offerNotFound);
    }

    private SlotOffer load(UUID offerId) {
        return offers.findById(offerId).orElseThrow(ReallocationErrors::offerNotFound);
    }

    private static OfferView view(SlotOffer offer, SlotView slot) {
        return new OfferView(offer.id(), offer.slotId(), offer.referralId(), offer.patientId(), slot.unitId(),
                slot.specialtyId(), slot.startAt(), offer.expiresAt(), offer.round(), offer.status());
    }
}
