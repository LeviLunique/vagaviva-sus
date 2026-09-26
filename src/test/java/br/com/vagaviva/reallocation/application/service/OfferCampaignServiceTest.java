package br.com.vagaviva.reallocation.application.service;

import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.CLOCK;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.NOW;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.POLICY;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.SPECIALTY;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.START;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.UNIT;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.at;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.openSlot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.reallocation.events.SlotOffered;
import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.SlotView;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OfferCampaignServiceTest {

    private static final UUID SLOT = UUID.randomUUID();

    @Mock SlotOfferRepository offers;
    @Mock SchedulingApi scheduling;
    @Mock QueueApi queue;
    @Mock CatalogApi catalog;
    @Mock ReallocationAudit audit;
    @Mock ApplicationEventPublisher events;

    private OfferCampaignService service;

    @BeforeEach
    void setUp() {
        service = new OfferCampaignService(offers, scheduling, queue, catalog, POLICY, audit, events, CLOCK);
        lenient().when(offers.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(catalog.findUnit(UNIT)).thenReturn(Optional.of(new HealthUnitSummary(UNIT, "9900201",
                "AME Zona Norte", HealthUnitType.SPECIALIZED, "3550308", "São Paulo", "Av. Norte, 1500",
                Set.of("3550308"), true)));
    }

    private static ReferralCandidate candidate() {
        return new ReferralCandidate(UUID.randomUUID(), UUID.randomUUID(), SPECIALTY, RiskClass.YELLOW, false,
                NOW.minus(Duration.ofDays(30)), true);
    }

    @Test
    @DisplayName("RF-31: rodada 1 trava até 3 candidatos que aceitam encaixe e cria uma oferta para cada")
    void shouldOpenFirstRound() {
        Set<UUID> excluded = Set.of(UUID.randomUUID());
        List<ReferralCandidate> candidates = List.of(candidate(), candidate(), candidate());
        when(scheduling.findSlotView(SLOT)).thenReturn(Optional.of(openSlot(SLOT)));
        when(offers.findPatientsToExclude(SLOT)).thenReturn(excluded);
        when(queue.lockShortNoticeCandidates(SPECIALTY, new EligibilityCriteria(Set.of("3550308")), excluded, 3))
                .thenReturn(candidates);

        service.advance(SLOT);

        var saved = ArgumentCaptor.forClass(SlotOffer.class);
        verify(offers, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(offer -> {
            assertThat(offer.round()).isEqualTo(1);
            assertThat(offer.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
        });
        assertThat(saved.getAllValues()).extracting(SlotOffer::referralId)
                .containsExactlyElementsOf(candidates.stream().map(ReferralCandidate::referralId).toList());
        verify(events, times(3)).publishEvent(any(SlotOffered.class));
        verify(audit).record(eq(ReallocationAudit.OFFER_ROUND_STARTED), eq(ReallocationAudit.SLOT), eq(SLOT), any());
        verify(scheduling, never()).markSlotLost(any());
    }

    @Test
    @DisplayName("rodada seguinte numerada a partir da última; ainda há pendente ⇒ espera")
    void shouldContinueCascade() {
        when(scheduling.findSlotView(SLOT)).thenReturn(Optional.of(openSlot(SLOT)));
        when(offers.findLastRound(SLOT)).thenReturn(2);
        when(queue.lockShortNoticeCandidates(any(), any(), any(), anyInt())).thenReturn(List.of(candidate()));

        service.advance(SLOT);

        verify(offers).save(org.mockito.ArgumentMatchers.argThat(offer -> offer.round() == 3));

        when(offers.existsPending(SLOT)).thenReturn(true);
        service.advance(SLOT);
        verify(offers, times(1)).save(any());
    }

    @Test
    @DisplayName("RN-15: sem candidatos, rodadas esgotadas ou em cima da hora ⇒ vaga perdida")
    void shouldLoseSlotWhenCascadeEnds() {
        when(scheduling.findSlotView(SLOT)).thenReturn(Optional.of(openSlot(SLOT)));
        when(queue.lockShortNoticeCandidates(any(), any(), any(), anyInt())).thenReturn(List.of());
        service.advance(SLOT);

        when(offers.findLastRound(SLOT)).thenReturn(5);
        service.advance(SLOT);

        when(offers.findLastRound(SLOT)).thenReturn(0);
        new OfferCampaignService(offers, scheduling, queue, catalog, POLICY, audit, events,
                at(START.minus(Duration.ofHours(2)))).advance(SLOT);

        verify(scheduling, times(3)).markSlotLost(SLOT);
        verify(offers, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("vaga que já saiu da oferta (aceita, cancelada) ou inexistente ⇒ nada acontece")
    void shouldIgnoreClosedSlots() {
        UUID other = UUID.randomUUID();
        when(scheduling.findSlotView(SLOT)).thenReturn(Optional.of(new SlotView(SLOT, UNIT, SPECIALTY, START,
                SlotStatus.ALLOCATED)));
        when(scheduling.findSlotView(other)).thenReturn(Optional.empty());

        service.advance(SLOT);
        service.advance(other);

        verifyNoInteractions(queue, events);
        verify(scheduling, never()).markSlotLost(any());
    }
}
