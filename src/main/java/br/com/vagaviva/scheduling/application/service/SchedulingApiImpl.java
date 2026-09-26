package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.SlotReleased;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada da agenda para o engajamento. Ações do paciente sem ator humano identificado: a
 * auditoria (com o token usado) fica com o módulo que recebeu o clique no link.
 */
@Service
class SchedulingApiImpl implements SchedulingApi {

    private final AppointmentRepository appointments;
    private final QueueApi queue;
    private final SlotReleaser releaser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SchedulingApiImpl(AppointmentRepository appointments, QueueApi queue, SlotReleaser releaser,
            ApplicationEventPublisher events, Clock clock) {
        this.appointments = appointments;
        this.queue = queue;
        this.releaser = releaser;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppointmentView> findAppointmentView(UUID appointmentId) {
        return appointments.findById(appointmentId).map(SchedulingApiImpl::view);
    }

    @Override
    @Transactional
    public AppointmentView confirm(UUID appointmentId) {
        Appointment appointment = load(appointmentId);
        if (appointment.confirm(clock)) {
            appointment = appointments.save(appointment);
            events.publishEvent(new AppointmentConfirmed(appointment.id(), appointment.referralId(),
                    appointment.unitId(), appointment.specialtyId(), appointment.startAt()));
        }
        return view(appointment);
    }

    @Override
    @Transactional
    public AppointmentView cancelByPatient(UUID appointmentId) {
        Appointment appointment = load(appointmentId);
        if (appointment.cancelByPatient(clock)) {
            appointment = appointments.save(appointment);
            queue.returnToQueue(appointment.referralId(), ReturnReason.PATIENT_CANCELLED);
            releaser.release(appointment.slotId(), SlotReleased.Reason.PATIENT_CANCELLED);
            publishCancelled(appointment, AppointmentCancelled.Reason.PATIENT);
        }
        return view(appointment);
    }

    @Override
    @Transactional
    public AppointmentView withdraw(UUID appointmentId) {
        Appointment appointment = load(appointmentId);
        if (appointment.withdraw(clock)) {
            appointment = appointments.save(appointment);
            queue.withdraw(appointment.referralId());
            releaser.release(appointment.slotId(), SlotReleased.Reason.WITHDRAWN);
            publishCancelled(appointment, AppointmentCancelled.Reason.WITHDRAWN);
        }
        return view(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> findAppointmentsNeedingReminder(ReminderType type, Instant from, Instant to) {
        return appointments.findForReminder(type, from, to).stream().map(SchedulingApiImpl::view).toList();
    }

    private Appointment load(UUID appointmentId) {
        return appointments.findById(appointmentId).orElseThrow(SchedulingErrors::appointmentNotFound);
    }

    private void publishCancelled(Appointment appointment, AppointmentCancelled.Reason reason) {
        events.publishEvent(new AppointmentCancelled(appointment.id(), appointment.slotId(), appointment.referralId(),
                appointment.patientId(), appointment.startAt(), reason));
    }

    private static AppointmentView view(Appointment a) {
        return new AppointmentView(a.id(), a.slotId(), a.referralId(), a.patientId(), a.unitId(), a.specialtyId(),
                a.startAt(), a.origin(), a.status(), a.confirmationDeadline());
    }
}
