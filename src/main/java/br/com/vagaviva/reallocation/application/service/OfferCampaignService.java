package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.OfferRoundPolicy;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.reallocation.events.SlotOffered;
import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.SlotView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encaixe em cascata (RF-31): cada rodada trava ({@code SKIP LOCKED}) os próximos da fila que aceitam
 * encaixe, na ordem oficial, e cria uma oferta para cada. A rodada seguinte só abre quando nenhuma
 * oferta da vaga está pendente; sem rodada possível (limite, prazo ou ninguém elegível) a vaga é perdida.
 */
@Service
class OfferCampaignService implements StartOfferCampaignUseCase {

    private static final Logger log = LoggerFactory.getLogger(OfferCampaignService.class);

    private final SlotOfferRepository offers;
    private final SchedulingApi scheduling;
    private final QueueApi queue;
    private final CatalogApi catalog;
    private final OfferRoundPolicy policy;
    private final ReallocationAudit audit;
    private final OfferMetrics metrics;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OfferCampaignService(SlotOfferRepository offers, SchedulingApi scheduling, QueueApi queue, CatalogApi catalog,
            OfferRoundPolicy policy, ReallocationAudit audit, OfferMetrics metrics, ApplicationEventPublisher events,
            Clock clock) {
        this.offers = offers;
        this.scheduling = scheduling;
        this.queue = queue;
        this.catalog = catalog;
        this.policy = policy;
        this.audit = audit;
        this.metrics = metrics;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void advance(UUID slotId) {
        Optional<SlotView> found = scheduling.findSlotView(slotId);
        if (found.isEmpty() || found.get().status() != SlotStatus.OPEN_FOR_OFFERS || offers.existsPending(slotId)) {
            return;
        }
        SlotView slot = found.get();
        Instant now = clock.instant();
        int round = offers.findLastRound(slotId) + 1;
        if (!policy.canOpenRound(round, slot.startAt(), now)) {
            loseSlot(slot, "rodadas ou prazo esgotados");
            return;
        }
        EligibilityCriteria criteria = new EligibilityCriteria(catalog.findUnit(slot.unitId())
                .map(HealthUnitSummary::serviceArea).orElseThrow());
        List<ReferralCandidate> candidates = queue.lockShortNoticeCandidates(slot.specialtyId(), criteria,
                offers.findPatientsToExclude(slotId), policy.batchSize());
        if (candidates.isEmpty()) {
            loseSlot(slot, "ninguém elegível aceita encaixe");
            return;
        }
        Instant expiresAt = policy.expiresAt(slot.startAt(), now);
        for (ReferralCandidate candidate : candidates) {
            SlotOffer offer = offers.save(SlotOffer.create(slotId, candidate.referralId(), candidate.patientId(), round,
                    expiresAt, clock));
            events.publishEvent(new SlotOffered(offer.id(), slotId, offer.patientId(), offer.referralId(),
                    slot.startAt(), expiresAt));
        }
        metrics.count(OfferStatus.PENDING, candidates.size());
        audit.record(ReallocationAudit.OFFER_ROUND_STARTED, ReallocationAudit.SLOT, slotId,
                Map.of("round", String.valueOf(round), "offers", String.valueOf(candidates.size())));
    }

    private void loseSlot(SlotView slot, String reason) {
        log.info("Encaixe da vaga {} encerrado sem aceite: {}.", slot.id(), reason);
        scheduling.markSlotLost(slot.id());
    }
}
