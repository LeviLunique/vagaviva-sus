package br.com.vagaviva.scheduling.application.service;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CLOCK;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.LEAD_TIMES;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.NOW;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.SPECIALTY;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.ReferralView;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OfferAllocationTest {

    private static final Instant START = NOW.plus(Duration.ofDays(1));

    @Mock AppointmentRepository appointments;
    @Mock SlotRepository slots;
    @Mock QueueApi queue;
    @Mock SchedulingAudit audit;
    @Mock ApplicationEventPublisher events;

    private SchedulingApiImpl api;

    @BeforeEach
    void setUp() {
        api = new SchedulingApiImpl(appointments, slots, queue, null, audit, events, CLOCK);
        lenient().when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(slots.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Slot openSlot() {
        Slot slot = Slot.publish(UNIT, SPECIALTY, "Dra. Encaixe", START, 30, LEAD_TIMES, CLOCK);
        assertThat(slot.status()).isEqualTo(SlotStatus.OPEN_FOR_OFFERS);
        return slot;
    }

    private static ReferralView referral(UUID id, UUID patient) {
        return new ReferralView(id, "VV-2026-0000001", patient, SPECIALTY, UUID.randomUUID(), ReferralStatus.WAITING,
                RiskClass.YELLOW, true, "3550308", NOW.minus(Duration.ofDays(40)));
    }

    @Test
    @DisplayName("RN-18: aceite toma a vaga, cria agendamento CONFIRMED (SHORT_NOTICE_OFFER) e agenda o encaminhamento")
    void shouldAllocateFromOffer() {
        Slot open = openSlot();
        UUID referralId = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        Slot claimed = Slot.restore(open.id(), UNIT, SPECIALTY, "Dra. Encaixe", START, 30, SlotStatus.ALLOCATED, 0,
                NOW, NOW, 1L);
        when(queue.findView(referralId)).thenReturn(Optional.of(referral(referralId, patient)));
        when(slots.claimOpenForOffers(open.id(), NOW)).thenReturn(true);
        when(slots.findById(open.id())).thenReturn(Optional.of(claimed));

        var view = api.allocateFromOffer(open.id(), referralId);

        assertThat(view.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(view.origin()).isEqualTo(AppointmentOrigin.SHORT_NOTICE_OFFER);
        assertThat(view.confirmationDeadline()).isNull();
        assertThat(view.patientId()).isEqualTo(patient);
        verify(queue).markScheduled(referralId);
        verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentScheduled s
                && s.origin() == AppointmentOrigin.SHORT_NOTICE_OFFER
                && s.queueEnteredAt().equals(NOW.minus(Duration.ofDays(40)))));
        verify(audit).record(eq(null), eq(SchedulingAudit.APPOINTMENT_SCHEDULED), eq(SchedulingAudit.APPOINTMENT),
                eq(view.id()), eq(null), any());
    }

    @Test
    @DisplayName("RF-32: vaga já tomada (0 linhas) ⇒ 409 SLOT_ALREADY_FILLED, nada é criado; encaminhamento inexistente ⇒ 404")
    void shouldRejectFilledSlot() {
        UUID slotId = UUID.randomUUID();
        UUID referralId = UUID.randomUUID();
        when(queue.findView(referralId)).thenReturn(Optional.of(referral(referralId, UUID.randomUUID())));
        when(slots.claimOpenForOffers(slotId, NOW)).thenReturn(false);

        assertThatThrownBy(() -> api.allocateFromOffer(slotId, referralId)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_ALREADY_FILLED");
        assertThatThrownBy(() -> api.allocateFromOffer(slotId, UUID.randomUUID())).isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("REFERRAL_NOT_FOUND");
        verify(appointments, never()).save(any());
        verify(queue, never()).markScheduled(any());
    }

    @Test
    @DisplayName("RN-15: encaixe esgotado ⇒ vaga EXPIRED + SlotLost; vaga que já saiu da oferta é ignorada")
    void shouldMarkSlotLost() {
        Slot open = openSlot();
        Slot allocated = Slot.restore(UUID.randomUUID(), UNIT, SPECIALTY, "Dra. Encaixe", START, 30,
                SlotStatus.ALLOCATED, 0, NOW, NOW, 1L);
        when(slots.findById(open.id())).thenReturn(Optional.of(open));
        when(slots.findById(allocated.id())).thenReturn(Optional.of(allocated));

        api.markSlotLost(open.id());
        api.markSlotLost(allocated.id());

        assertThat(open.status()).isEqualTo(SlotStatus.EXPIRED);
        assertThat(allocated.status()).isEqualTo(SlotStatus.ALLOCATED);
        verify(events).publishEvent(new SlotLost(open.id(), UNIT, SPECIALTY, START));
        verify(audit).record(null, SchedulingAudit.SLOT_LOST, SchedulingAudit.SLOT, open.id(), null,
                java.util.Map.of("reason", "OFFERS_EXHAUSTED"));
    }

    @Test
    @DisplayName("F6: visão da vaga para o encaixe; inexistente ⇒ vazio")
    void shouldExposeSlotView() {
        Slot open = openSlot();
        when(slots.findById(open.id())).thenReturn(Optional.of(open));

        assertThat(api.findSlotView(open.id())).hasValueSatisfying(view -> {
            assertThat(view.unitId()).isEqualTo(UNIT);
            assertThat(view.startAt()).isEqualTo(START);
            assertThat(view.status()).isEqualTo(SlotStatus.OPEN_FOR_OFFERS);
        });
        assertThat(api.findSlotView(UUID.randomUUID())).isEmpty();
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("domínio: só vaga ALLOCATED vira agendamento de encaixe; só vaga em oferta é dada como perdida")
    void shouldGuardDomainTransitions() {
        Slot open = openSlot();
        assertThatThrownBy(() -> br.com.vagaviva.scheduling.domain.Appointment.fromOffer(open, UUID.randomUUID(),
                UUID.randomUUID(), CLOCK)).isInstanceOf(ConflictException.class);
        Slot allocated = Slot.restore(UUID.randomUUID(), UNIT, SPECIALTY, "Dra. Encaixe", START, 30,
                SlotStatus.ALLOCATED, 0, NOW, NOW, 1L);
        assertThatThrownBy(() -> allocated.markLost(CLOCK)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_INVALID_STATE");
    }
}
