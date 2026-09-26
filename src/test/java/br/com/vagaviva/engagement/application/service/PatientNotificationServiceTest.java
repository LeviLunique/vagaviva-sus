package br.com.vagaviva.engagement.application.service;

import static br.com.vagaviva.engagement.fixtures.EngagementFixture.CLOCK;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.NOW;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.SPECIALTY;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.UNIT;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.appointment;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.patient;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.properties;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.specialty;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.application.port.out.PatientActionTokenRepository;
import br.com.vagaviva.engagement.application.service.EngagementProperties.ChannelMode;
import br.com.vagaviva.engagement.domain.MessageComposer;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationType;
import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.engagement.events.NotificationDispatchRequested;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.ReferralView;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SchedulingApi.ReminderType;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PatientNotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock PatientActionTokenRepository tokens;
    @Mock PatientApi patients;
    @Mock CatalogApi catalog;
    @Mock QueueApi queue;
    @Mock SchedulingApi scheduling;
    @Mock ApplicationEventPublisher events;
    @Mock PlatformTransactionManager transactionManager;

    private PatientNotificationService service;

    @BeforeEach
    void setUp() {
        var properties = properties(ChannelMode.SANDBOX, false, false);
        service = new PatientNotificationService(notifications, tokens, patients, catalog, queue, scheduling,
                new MessageComposer(CLOCK.getZone()), new ChannelSelector(properties), properties, events,
                transactionManager, "https://vagaviva.test/", CLOCK);
        lenient().when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(catalog.findUnit(UNIT)).thenReturn(Optional.of(unit()));
        lenient().when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.of(specialty(SpecialtyType.CONSULTATION, true)));
    }

    private Notification saved() {
        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("entrada na fila: mensagem com protocolo e rótulo genérico (nunca a especialidade sensível) + pedido de envio")
    void shouldNotifyReferralQueued() {
        UUID patientId = UUID.randomUUID();
        UUID referralId = UUID.randomUUID();
        when(patients.findSummary(patientId)).thenReturn(Optional.of(patient(patientId, false, true)));
        when(queue.findView(referralId)).thenReturn(Optional.of(new ReferralView(referralId, "VV-2026-0000123", patientId,
                SPECIALTY, UNIT, ReferralStatus.WAITING, RiskClass.RED, false, "3550308")));

        service.referralQueued(new ReferralQueued(referralId, patientId, "VV-2026-0000123"));

        Notification notification = saved();
        assertThat(notification.type()).isEqualTo(NotificationType.REFERRAL_QUEUED);
        assertThat(notification.channel()).isEqualTo(NotificationChannel.SANDBOX);
        assertThat(notification.body()).contains("VV-2026-0000123").contains("sua consulta").doesNotContain("Psiquiatria");
        assertThat(notification.destinationHash()).isEqualTo(PatientActionToken.hash("+5511999990001"));
        verify(events).publishEvent(new NotificationDispatchRequested(notification.id()));
    }

    @Test
    @DisplayName("RN-24: marco já notificado não gera segunda mensagem; paciente inativo não recebe")
    void shouldBeIdempotentAndSkipInactivePatients() {
        UUID referralId = UUID.randomUUID();
        when(notifications.existsForReferral(referralId, NotificationType.REFERRAL_QUEUED)).thenReturn(true);
        service.referralQueued(new ReferralQueued(referralId, UUID.randomUUID(), "VV-2026-0000001"));

        UUID inactive = UUID.randomUUID();
        when(patients.findSummary(inactive)).thenReturn(Optional.of(patient(inactive, false, false)));
        service.referralQueued(new ReferralQueued(UUID.randomUUID(), inactive, "VV-2026-0000002"));

        verify(notifications, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("RF-25: agendamento gera token próprio (só o hash guardado) e link curto na mensagem")
    void shouldNotifyAppointmentWithLink() {
        AppointmentView view = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        when(scheduling.findAppointmentView(view.id())).thenReturn(Optional.of(view));
        when(patients.findSummary(view.patientId())).thenReturn(Optional.of(patient(view.patientId(), true, true)));

        service.appointmentScheduled(new AppointmentScheduled(view.id(), view.slotId(), view.referralId(),
                view.patientId(), UNIT, SPECIALTY, view.startAt(), AppointmentOrigin.REGULAR, view.confirmationDeadline(),
                NOW.minus(Duration.ofDays(30))));

        Notification notification = saved();
        assertThat(notification.body()).containsPattern("https://vagaviva\\.test/p/[A-Za-z0-9_-]{22}").contains("AME Zona Norte");
        verify(tokens).save(argThat(token -> token.subjectId().equals(view.id()) && token.expiresAt().equals(view.startAt())
                && !notification.body().contains(token.tokenHash())));
    }

    @Test
    @DisplayName("encaixe aceito (F6) não pede confirmação; cancelamento só avisa quando foi a unidade (sem link)")
    void shouldFilterSchedulingEvents() {
        service.appointmentScheduled(new AppointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UNIT, SPECIALTY, NOW, AppointmentOrigin.SHORT_NOTICE_OFFER, NOW, NOW));
        service.appointmentCancelled(new AppointmentCancelled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, AppointmentCancelled.Reason.PATIENT));
        verifyNoInteractions(scheduling);

        AppointmentView view = appointment(AppointmentStatus.CANCELLED_BY_UNIT);
        when(scheduling.findAppointmentView(view.id())).thenReturn(Optional.of(view));
        when(patients.findSummary(view.patientId())).thenReturn(Optional.of(patient(view.patientId(), false, true)));
        service.appointmentCancelled(new AppointmentCancelled(view.id(), view.slotId(), view.referralId(),
                view.patientId(), view.startAt(), AppointmentCancelled.Reason.UNIT));

        Notification notification = saved();
        assertThat(notification.type()).isEqualTo(NotificationType.APPOINTMENT_CANCELLED_BY_UNIT);
        assertThat(notification.body()).contains("mesma posição").doesNotContain("/p/");
        verifyNoInteractions(tokens);
    }

    @Test
    @DisplayName("RN-11: lembretes de confirmação e de véspera, um por agendamento; falha isolada")
    void shouldSendDueReminders() {
        AppointmentView pending = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        AppointmentView confirmed = appointment(AppointmentStatus.CONFIRMED);
        AppointmentView broken = appointment(AppointmentStatus.CONFIRMED);
        AppointmentView alreadyReminded = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        when(scheduling.findAppointmentsNeedingReminder(ReminderType.CONFIRMATION, NOW, NOW.plus(Duration.ofHours(24))))
                .thenReturn(List.of(pending, alreadyReminded));
        when(scheduling.findAppointmentsNeedingReminder(ReminderType.ATTENDANCE, NOW, NOW.plus(Duration.ofHours(24))))
                .thenReturn(List.of(confirmed, broken));
        lenient().when(notifications.existsForAppointment(alreadyReminded.id(), NotificationType.CONFIRMATION_REMINDER)).thenReturn(true);
        when(patients.findSummary(any())).thenAnswer(call -> Optional.of(patient(call.getArgument(0), false, true)));
        lenient().when(notifications.existsForAppointment(eq(broken.id()), any())).thenThrow(new IllegalStateException("falha"));

        assertThat(service.sendDueReminders()).isEqualTo(2);
    }
}
