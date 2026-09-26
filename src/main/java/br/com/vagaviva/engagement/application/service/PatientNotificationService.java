package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.application.port.in.NotifyPatientUseCases;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.application.port.out.PatientActionTokenRepository;
import br.com.vagaviva.engagement.domain.CareLabel;
import br.com.vagaviva.engagement.domain.MessageComposer;
import br.com.vagaviva.engagement.domain.MessageComposer.MessageData;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationType;
import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.engagement.domain.TokenPurpose;
import br.com.vagaviva.engagement.events.NotificationDispatchRequested;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralView;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SchedulingApi.ReminderType;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF-24: cria a mensagem de cada marco e pede o envio (evento no outbox ⇒ fila SQS). Idempotente
 * (RN-24): um marco reprocessado não gera segunda mensagem. Cada mensagem com link recebe um token
 * próprio (RF-25), válido até o início do atendimento.
 */
@Service
class PatientNotificationService implements NotifyPatientUseCases {

    private static final Logger log = LoggerFactory.getLogger(PatientNotificationService.class);

    private final NotificationRepository notifications;
    private final PatientActionTokenRepository tokens;
    private final PatientApi patients;
    private final CatalogApi catalog;
    private final QueueApi queue;
    private final SchedulingApi scheduling;
    private final MessageComposer composer;
    private final ChannelSelector channels;
    private final EngagementProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate perReminder;
    private final String publicBaseUrl;
    private final Clock clock;

    PatientNotificationService(NotificationRepository notifications, PatientActionTokenRepository tokens,
            PatientApi patients, CatalogApi catalog, QueueApi queue, SchedulingApi scheduling, MessageComposer composer,
            ChannelSelector channels, EngagementProperties properties, ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager, @Value("${vagaviva.public-base-url}") String publicBaseUrl,
            Clock clock) {
        this.notifications = notifications;
        this.tokens = tokens;
        this.patients = patients;
        this.catalog = catalog;
        this.queue = queue;
        this.scheduling = scheduling;
        this.composer = composer;
        this.channels = channels;
        this.properties = properties;
        this.events = events;
        this.perReminder = new TransactionTemplate(transactionManager);
        this.perReminder.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void referralQueued(ReferralQueued event) {
        if (notifications.existsForReferral(event.referralId(), NotificationType.REFERRAL_QUEUED)) {
            return;
        }
        activePatient(event.patientId()).ifPresent(patient -> {
            CareLabel label = queue.findView(event.referralId()).map(ReferralView::specialtyId).map(this::labelOf)
                    .orElse(CareLabel.CONSULTATION);
            NotificationChannel channel = channels.channelFor(patient);
            String body = composer.compose(NotificationType.REFERRAL_QUEUED,
                    new MessageData(patient.firstName(), label, event.protocol(), null, null, null, null), channel);
            save(Notification.create(patient.id(), null, null, event.referralId(), NotificationType.REFERRAL_QUEUED,
                    channel, PatientActionToken.hash(patient.phone()), body, clock));
        });
    }

    /** Encaixe aceito (F6) já nasce confirmado: não recebe o pedido de confirmação. */
    @Override
    @Transactional
    public void appointmentScheduled(AppointmentScheduled event) {
        if (event.origin() == AppointmentOrigin.SHORT_NOTICE_OFFER) {
            return;
        }
        scheduling.findAppointmentView(event.appointmentId())
                .ifPresent(view -> notifyAppointment(view, NotificationType.APPOINTMENT_SCHEDULED));
    }

    @Override
    @Transactional
    public void appointmentCancelled(AppointmentCancelled event) {
        if (event.reason() != AppointmentCancelled.Reason.UNIT) {
            return;
        }
        scheduling.findAppointmentView(event.appointmentId())
                .ifPresent(view -> notifyAppointment(view, NotificationType.APPOINTMENT_CANCELLED_BY_UNIT));
    }

    /** Lembretes das próximas 24 h; cada um na sua transação (um problema não impede os demais). */
    @Override
    public int sendDueReminders() {
        Instant now = clock.instant();
        int created = 0;
        for (AppointmentView view : scheduling.findAppointmentsNeedingReminder(ReminderType.CONFIRMATION, now,
                now.plus(properties.confirmationReminderBeforeDeadline()))) {
            created += remind(view, NotificationType.CONFIRMATION_REMINDER);
        }
        for (AppointmentView view : scheduling.findAppointmentsNeedingReminder(ReminderType.ATTENDANCE, now,
                now.plus(properties.attendanceReminderBeforeStart()))) {
            created += remind(view, NotificationType.ATTENDANCE_REMINDER);
        }
        return created;
    }

    private int remind(AppointmentView view, NotificationType type) {
        try {
            return Boolean.TRUE.equals(perReminder.execute(status -> notifyAppointment(view, type))) ? 1 : 0;
        } catch (RuntimeException ex) {
            log.warn("Falha ao criar lembrete {} do agendamento {}: {}", type, view.id(), ex.getMessage());
            return 0;
        }
    }

    private boolean notifyAppointment(AppointmentView view, NotificationType type) {
        if (notifications.existsForAppointment(view.id(), type)) {
            return false;
        }
        Optional<PatientSummary> patient = activePatient(view.patientId());
        if (patient.isEmpty()) {
            return false;
        }
        HealthUnitSummary unit = catalog.findUnit(view.unitId()).orElseThrow();
        String link = type == NotificationType.APPOINTMENT_CANCELLED_BY_UNIT ? null : issueLink(view);
        NotificationChannel channel = channels.channelFor(patient.get());
        String body = composer.compose(type, new MessageData(patient.get().firstName(), labelOf(view.specialtyId()),
                null, view.startAt(), view.confirmationDeadline(), unit.name(), link), channel);
        save(Notification.create(view.patientId(), view.id(), null, view.referralId(), type, channel,
                PatientActionToken.hash(patient.get().phone()), body, clock));
        return true;
    }

    private String issueLink(AppointmentView view) {
        var issued = PatientActionToken.issue(TokenPurpose.APPOINTMENT, view.id(), view.startAt(), clock);
        tokens.save(issued.token());
        return publicBaseUrl + "/p/" + issued.raw();
    }

    private void save(Notification notification) {
        Notification saved = notifications.save(notification);
        events.publishEvent(new NotificationDispatchRequested(saved.id()));
    }

    private Optional<PatientSummary> activePatient(UUID patientId) {
        Optional<PatientSummary> patient = patients.findSummary(patientId).filter(PatientSummary::active);
        if (patient.isEmpty()) {
            log.warn("Paciente {} inexistente ou inativo: mensagem não criada.", patientId);
        }
        return patient;
    }

    private CareLabel labelOf(@Nullable UUID specialtyId) {
        return catalog.findSpecialty(specialtyId)
                .map(specialty -> specialty.type() == SpecialtyType.EXAM ? CareLabel.EXAM : CareLabel.CONSULTATION)
                .orElse(CareLabel.CONSULTATION);
    }
}
