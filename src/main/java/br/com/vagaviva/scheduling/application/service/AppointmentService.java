package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReviewReason;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AppointmentService implements AppointmentUseCases {

    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final PatientApi patients;
    private final QueueApi queue;
    private final SchedulingAudit audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AppointmentService(AppointmentRepository appointments, SlotRepository slots, PatientApi patients, QueueApi queue,
            SchedulingAudit audit, ApplicationEventPublisher events, Clock clock) {
        this.appointments = appointments;
        this.slots = slots;
        this.patients = patients;
        this.queue = queue;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Appointment get(UUID appointmentId, CurrentUser actor, @Nullable String clientIp) {
        Appointment appointment = load(appointmentId, actor);
        audit.record(actor, SchedulingAudit.APPOINTMENT_READ, SchedulingAudit.APPOINTMENT, appointmentId, clientIp,
                Map.of());
        return appointment;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentItem> list(AppointmentFilter filter, Pageable pageable, CurrentUser actor) {
        AppointmentFilter effective = new AppointmentFilter(UnitScope.effectiveUnit(actor, filter.unitId()),
                filter.date(), filter.status());
        Page<Appointment> page = appointments.search(effective, pageable);
        Map<UUID, PatientSummary> summaries = patients.findSummaries(
                page.getContent().stream().map(Appointment::patientId).toList());
        return page.map(appointment -> item(appointment, summaries.get(appointment.patientId())));
    }

    @Override
    @Transactional
    public Appointment checkIn(UUID appointmentId, CurrentUser actor, @Nullable String clientIp) {
        Appointment appointment = load(appointmentId, actor);
        appointment.checkIn(clock);
        Slot slot = slots.findById(appointment.slotId()).orElseThrow(SchedulingErrors::slotNotFound);
        slot.markUsed(clock);
        slots.save(slot);
        Appointment saved = appointments.save(appointment);
        queue.markCompleted(appointment.referralId());
        events.publishEvent(new AppointmentAttended(saved.id(), saved.referralId(), saved.unitId(), saved.specialtyId(),
                saved.startAt()));
        audit.record(actor, SchedulingAudit.APPOINTMENT_ATTENDED, SchedulingAudit.APPOINTMENT, saved.id(), clientIp,
                Map.of());
        return saved;
    }

    @Override
    @Transactional
    public Appointment markNoShow(UUID appointmentId, CurrentUser actor, @Nullable String clientIp) {
        Appointment appointment = load(appointmentId, actor);
        appointment.markNoShow(clock);
        Slot slot = slots.findById(appointment.slotId()).orElseThrow(SchedulingErrors::slotNotFound);
        slot.markMissed(clock);
        slots.save(slot);
        Appointment saved = appointments.save(appointment);
        queue.sendToReview(appointment.referralId(), ReviewReason.NO_SHOW);
        events.publishEvent(new AppointmentMissed(saved.id(), saved.referralId(), saved.unitId(), saved.specialtyId(),
                saved.startAt()));
        audit.record(actor, SchedulingAudit.APPOINTMENT_NO_SHOW, SchedulingAudit.APPOINTMENT, saved.id(), clientIp,
                Map.of());
        return saved;
    }

    private Appointment load(UUID appointmentId, CurrentUser actor) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow(SchedulingErrors::appointmentNotFound);
        UnitScope.requireAccess(actor, appointment.unitId());
        return appointment;
    }

    private static AppointmentItem item(Appointment appointment, @Nullable PatientSummary patient) {
        return new AppointmentItem(appointment.id(), appointment.slotId(), appointment.referralId(),
                appointment.patientId(), patient == null ? null : patient.firstName(),
                patient == null ? null : patient.cnsMasked(), appointment.unitId(), appointment.specialtyId(),
                appointment.startAt(), appointment.origin(), appointment.status(), appointment.confirmationDeadline());
    }
}
