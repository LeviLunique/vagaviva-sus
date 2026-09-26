package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.scheduling.application.port.in.ExpireConfirmationsUseCase;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.SlotReleased;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expira confirmações vencidas: {@code EXPIRED_UNCONFIRMED}, paciente de volta à fila com a data de
 * entrada original e mais uma não confirmação (RN-13 — na 2ª, vai para reavaliação) e vaga liberada
 * pela RN-15. Uma transação por agendamento, com {@code SKIP LOCKED}, como na alocação.
 */
@Service
class ConfirmationDeadlineService implements ExpireConfirmationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationDeadlineService.class);
    private static final int BATCH = 200;

    private final AppointmentRepository appointments;
    private final QueueApi queue;
    private final SlotReleaser releaser;
    private final SchedulingAudit audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate perAppointment;
    private final Clock clock;

    ConfirmationDeadlineService(AppointmentRepository appointments, QueueApi queue, SlotReleaser releaser,
            SchedulingAudit audit, ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.appointments = appointments;
        this.queue = queue;
        this.releaser = releaser;
        this.audit = audit;
        this.events = events;
        this.perAppointment = new TransactionTemplate(transactionManager);
        this.perAppointment.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public int expireOverdue() {
        List<UUID> overdue = appointments.findOverdueIds(clock.instant(), BATCH);
        int expired = 0;
        for (UUID id : overdue) {
            try {
                if (Boolean.TRUE.equals(perAppointment.execute(status -> expire(id)))) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.warn("Falha ao expirar o agendamento {}: {}", id, ex.getMessage());
            }
        }
        if (expired > 0) {
            log.info("{} agendamento(s) expirado(s) por falta de confirmação.", expired);
        }
        return expired;
    }

    @Override
    public void expireNow(UUID appointmentId) {
        perAppointment.executeWithoutResult(status -> {
            Appointment appointment = appointments.findById(appointmentId)
                    .orElseThrow(SchedulingErrors::appointmentNotFound);
            appointment.anticipateDeadline(clock.instant().minusSeconds(1));
            appointments.save(appointment);
        });
        perAppointment.execute(status -> expire(appointmentId));
    }

    private boolean expire(UUID appointmentId) {
        return appointments.lockPendingConfirmation(appointmentId).map(appointment -> {
            appointment.expireUnconfirmed(clock);
            Appointment saved = appointments.save(appointment);
            queue.returnToQueue(saved.referralId(), ReturnReason.UNCONFIRMED);
            releaser.release(saved.slotId(), SlotReleased.Reason.CONFIRMATION_EXPIRED);
            events.publishEvent(new AppointmentExpired(saved.id(), saved.slotId(), saved.referralId(),
                    saved.patientId(), saved.startAt()));
            audit.record(null, SchedulingAudit.APPOINTMENT_EXPIRED, SchedulingAudit.APPOINTMENT, saved.id(), null,
                    Map.of());
            return true;
        }).orElse(false);
    }
}
