package br.com.vagaviva.reallocation.application.service;

import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.CLOCK;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.NOW;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.START;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.UNIT;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.openSlot;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.pendingOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Aceite, recusa e visão da oferta. */
@ExtendWith(MockitoExtension.class)
class OfferResponseServiceTest {

    private static final UUID SLOT = UUID.randomUUID();

    @Mock SlotOfferRepository offers;
    @Mock SchedulingApi scheduling;
    @Mock StartOfferCampaignUseCase campaign;
    @Mock ReallocationAudit audit;
    @Mock ApplicationEventPublisher events;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private OfferResponseService service;

    @BeforeEach
    void setUp() {
        service = new OfferResponseService(offers, scheduling, campaign, audit, new OfferMetrics(meters), events, CLOCK);
        lenient().when(offers.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(scheduling.findSlotView(SLOT)).thenReturn(Optional.of(openSlot(SLOT)));
    }

    private SlotOffer stored() {
        SlotOffer offer = pendingOffer(SLOT, 2);
        when(offers.findById(offer.id())).thenReturn(Optional.of(offer));
        return offer;
    }

    @Test
    @DisplayName("RN-18: aceitar toma a vaga, marca ACCEPTED, substitui as demais e publica SlotOfferAccepted")
    void shouldAccept() {
        SlotOffer offer = stored();
        UUID appointment = UUID.randomUUID();
        when(scheduling.allocateFromOffer(SLOT, offer.referralId())).thenReturn(new AppointmentView(appointment, SLOT,
                offer.referralId(), offer.patientId(), UNIT, UUID.randomUUID(), START, AppointmentOrigin.SHORT_NOTICE_OFFER,
                AppointmentStatus.CONFIRMED, null));

        var accepted = service.accept(offer.id());

        assertThat(accepted.appointmentId()).isEqualTo(appointment);
        assertThat(offer.status()).isEqualTo(OfferStatus.ACCEPTED);
        verify(offers).supersedeOthers(SLOT, offer.id(), NOW);
        assertThat(meters.counter("vagaviva.offers", "status", "ACCEPTED").count()).isEqualTo(1.0);
        verify(events).publishEvent(new SlotOfferAccepted(offer.id(), SLOT, offer.referralId(), offer.patientId(),
                appointment, 2, START));
        verify(audit).record(eq(ReallocationAudit.OFFER_ACCEPTED), eq(ReallocationAudit.SLOT_OFFER), eq(offer.id()), any());
    }

    @Test
    @DisplayName("RF-32: vaga já tomada por outro ⇒ 409 SLOT_ALREADY_FILLED e nada é gravado; inexistente ⇒ 404")
    void shouldRejectWhenSlotTaken() {
        SlotOffer offer = stored();
        when(scheduling.allocateFromOffer(SLOT, offer.referralId()))
                .thenThrow(new ConflictException("SLOT_ALREADY_FILLED", "Vaga já preenchida."));

        assertThatThrownBy(() -> service.accept(offer.id())).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_ALREADY_FILLED");
        assertThatThrownBy(() -> service.accept(UUID.randomUUID())).isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("OFFER_NOT_FOUND");
        verify(offers, never()).save(any());
        verify(offers, never()).supersedeOthers(any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("recusar ⇒ DECLINED, auditado e a cascata avança; repetir não avança de novo")
    void shouldDecline() {
        SlotOffer offer = stored();

        var first = service.decline(offer.id());
        service.decline(offer.id());

        assertThat(first.status()).isEqualTo(OfferStatus.DECLINED);
        assertThat(first.unitId()).isEqualTo(UNIT);
        verify(campaign).advance(SLOT);
        verify(audit).record(eq(ReallocationAudit.OFFER_DECLINED), eq(ReallocationAudit.SLOT_OFFER), eq(offer.id()), any());
    }

    @Test
    @DisplayName("visão da oferta junta os dados da vaga; oferta inexistente ⇒ vazio")
    void shouldBuildView() {
        SlotOffer offer = stored();

        assertThat(service.findOfferView(offer.id())).hasValueSatisfying(view -> {
            assertThat(view.startAt()).isEqualTo(START);
            assertThat(view.status()).isEqualTo(OfferStatus.PENDING);
            assertThat(view.round()).isEqualTo(2);
        });
        assertThat(service.findOfferView(UUID.randomUUID())).isEmpty();
    }
}
