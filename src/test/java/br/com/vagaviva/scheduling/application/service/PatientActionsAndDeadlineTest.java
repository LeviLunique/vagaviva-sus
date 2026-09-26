package br.com.vagaviva.scheduling.application.service;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CLOCK;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.LEAD_TIMES;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.NOW;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.aPendingAppointment;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SchedulingApi.ReminderType;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.ReleasePolicy;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotOpenedForOffers;
import br.com.vagaviva.scheduling.events.SlotReleased;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PatientActionsAndDeadlineTest {

    @Mock AppointmentRepository appointments;
    @Mock SlotRepository slots;
    @Mock QueueApi queue;
    @Mock SchedulingAudit audit;
    @Mock ApplicationEventPublisher events;
    @Mock PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        lenient().when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(slots.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private SlotReleaser releaser(Clock clock) {
        return new SlotReleaser(slots, new ReleasePolicy(LEAD_TIMES), audit, events, clock);
    }

    private SchedulingApiImpl api(Clock clock) {
        return new SchedulingApiImpl(appointments, queue, releaser(clock), events, clock);
    }

    /** Agendamento pendente com a vaga (alocada) disponível no repositório. */
    private Appointment withAllocatedSlot() {
        Appointment appointment = aPendingAppointment();
        Slot slot = Slot.restore(appointment.slotId(), appointment.unitId(), appointment.specialtyId(), "Dra. Ana",
                appointment.startAt(), 30, SlotStatus.ALLOCATED, 0, NOW, NOW, 0L);
        lenient().when(slots.findById(appointment.slotId())).thenReturn(Optional.of(slot));
        when(appointments.findById(appointment.id())).thenReturn(Optional.of(appointment));
        return appointment;
    }

    @Test
    @DisplayName("confirmar publica AppointmentConfirmed só na primeira vez")
    void shouldConfirmOnce() {
        Appointment appointment = withAllocatedSlot();

        assertThat(api(CLOCK).confirm(appointment.id()).status()).isEqualTo(AppointmentStatus.CONFIRMED);
        api(CLOCK).confirm(appointment.id());

        verify(events, times(1)).publishEvent(argThat((Object e) -> e instanceof AppointmentConfirmed));
    }

    @Test
    @DisplayName("cancelar (≥ 5 dias): volta à fila na mesma posição e a vaga volta à alocação regular")
    void shouldCancelAndReturnSlotToRegularAllocation() {
        Appointment appointment = withAllocatedSlot();

        var view = api(CLOCK).cancelByPatient(appointment.id());

        assertThat(view.status()).isEqualTo(AppointmentStatus.CANCELLED_BY_PATIENT);
        verify(queue).returnToQueue(appointment.referralId(), ReturnReason.PATIENT_CANCELLED);
        verify(slots).save(argThat(slot -> slot.status() == SlotStatus.AVAILABLE && slot.releaseCount() == 1));
        verify(events).publishEvent(new SlotReleased(appointment.slotId(), SlotReleased.Reason.PATIENT_CANCELLED,
                SlotStatus.AVAILABLE, appointment.startAt()));
        verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentCancelled c
                && c.reason() == AppointmentCancelled.Reason.PATIENT));
    }

    @Test
    @DisplayName("desistir a 3 dias do início: sai da fila e a vaga vira oferta de encaixe (SlotOpenedForOffers)")
    void shouldWithdrawAndOpenSlotForOffers() {
        Appointment appointment = withAllocatedSlot();
        Clock threeDaysBefore = at(appointment.startAt().minus(Duration.ofDays(3)));

        api(threeDaysBefore).withdraw(appointment.id());
        api(threeDaysBefore).withdraw(appointment.id());

        verify(queue, times(1)).withdraw(appointment.referralId());
        verify(events).publishEvent(argThat((Object e) -> e instanceof SlotOpenedForOffers));
        verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentCancelled c
                && c.reason() == AppointmentCancelled.Reason.WITHDRAWN));
    }

    @Test
    @DisplayName("cancelar a 1 h do início: a vaga é perdida (SlotLost)")
    void shouldLoseSlotWhenTooLate() {
        Appointment appointment = withAllocatedSlot();

        api(at(appointment.startAt().minus(Duration.ofHours(1)))).cancelByPatient(appointment.id());

        verify(events).publishEvent(argThat((Object e) -> e instanceof SlotLost));
    }

    @Test
    @DisplayName("lembretes: delega ao repositório; agendamento inexistente ⇒ 404")
    void shouldFindRemindersAndRejectUnknown() {
        Appointment appointment = aPendingAppointment();
        Instant from = NOW;
        Instant to = NOW.plus(Duration.ofHours(24));
        when(appointments.findForReminder(ReminderType.CONFIRMATION, from, to)).thenReturn(List.of(appointment));

        assertThat(api(CLOCK).findAppointmentsNeedingReminder(ReminderType.CONFIRMATION, from, to))
                .extracting(view -> view.id()).containsExactly(appointment.id());
        assertThatThrownBy(() -> api(CLOCK).confirm(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("RF-27/RN-13: prazo vencido ⇒ EXPIRED_UNCONFIRMED, volta à fila (UNCONFIRMED), vaga liberada")
    void shouldExpireOverdueConfirmations() {
        Appointment overdue = aPendingAppointment();
        Slot slot = Slot.restore(overdue.slotId(), overdue.unitId(), overdue.specialtyId(), "Dra. Ana",
                overdue.startAt(), 30, SlotStatus.ALLOCATED, 0, NOW, NOW, 0L);
        Clock afterDeadline = at(overdue.confirmationDeadline().plusSeconds(60));
        UUID lockedElsewhere = UUID.randomUUID();
        when(appointments.findOverdueIds(afterDeadline.instant(), 200)).thenReturn(List.of(overdue.id(), lockedElsewhere));
        when(appointments.lockPendingConfirmation(overdue.id())).thenReturn(Optional.of(overdue));
        when(appointments.lockPendingConfirmation(lockedElsewhere)).thenReturn(Optional.empty());
        when(slots.findById(overdue.slotId())).thenReturn(Optional.of(slot));

        int expired = new ConfirmationDeadlineService(appointments, queue, releaser(afterDeadline), audit, events,
                transactionManager, afterDeadline).expireOverdue();

        assertThat(expired).isEqualTo(1);
        assertThat(overdue.status()).isEqualTo(AppointmentStatus.EXPIRED_UNCONFIRMED);
        verify(queue).returnToQueue(overdue.referralId(), ReturnReason.UNCONFIRMED);
        verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentExpired));
        verify(queue, never()).withdraw(any());
    }

    @Test
    @DisplayName("demonstração: antecipar o prazo e expirar na hora; falha isolada não derruba o lote")
    void shouldExpireNowAndIsolateFailures() {
        Appointment appointment = aPendingAppointment();
        Slot slot = Slot.restore(appointment.slotId(), appointment.unitId(), appointment.specialtyId(), "Dra. Ana",
                appointment.startAt(), 30, SlotStatus.ALLOCATED, 0, NOW, NOW, 0L);
        when(appointments.findById(appointment.id())).thenReturn(Optional.of(appointment));
        when(appointments.lockPendingConfirmation(appointment.id())).thenReturn(Optional.of(appointment));
        when(slots.findById(appointment.slotId())).thenReturn(Optional.of(slot));
        var service = new ConfirmationDeadlineService(appointments, queue, releaser(CLOCK), audit, events,
                transactionManager, CLOCK);

        service.expireNow(appointment.id());
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.EXPIRED_UNCONFIRMED);

        UUID broken = UUID.randomUUID();
        when(appointments.findOverdueIds(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(broken));
        when(appointments.lockPendingConfirmation(broken)).thenThrow(new IllegalStateException("falha"));
        assertThat(service.expireOverdue()).isZero();
    }
}
