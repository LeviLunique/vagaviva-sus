package br.com.vagaviva.insights.application.service;

import br.com.vagaviva.insights.application.port.in.ProjectEventsUseCase;
import br.com.vagaviva.insights.application.port.out.FactStore;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotReleased;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projeta os eventos nos fatos e conta as métricas de negócio (RF-36) — a métrica só incrementa quando a
 * projeção mudou uma linha, então o reprocessamento de um evento não conta duas vezes.
 */
@Service
class FactProjectionService implements ProjectEventsUseCase {

    static final String REALLOCATED = "REALLOCATED";
    static final String OFFER_ACCEPTED = "OFFER_ACCEPTED";
    static final String LOST = "LOST";
    private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(Duration.ofDays(1).toSeconds());

    private final FactStore facts;
    private final MeterRegistry meters;
    private final Clock clock;

    FactProjectionService(FactStore facts, MeterRegistry meters, Clock clock) {
        this.facts = facts;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void project(AppointmentScheduled e) {
        BigDecimal waitingDays = e.queueEnteredAt() == null ? null
                : BigDecimal.valueOf(Duration.between(e.queueEnteredAt(), e.startAt()).toSeconds())
                        .divide(SECONDS_PER_DAY, 1, RoundingMode.HALF_UP);
        boolean inserted = facts.insertAppointment(e.appointmentId(), e.specialtyId(), e.unitId(), e.origin().name(),
                clock.instant(), e.startAt(), e.queueEnteredAt(), e.origin() == AppointmentOrigin.SHORT_NOTICE_OFFER,
                waitingDays);
        if (inserted) {
            meters.counter("vagaviva.appointments.scheduled", "origin", e.origin().name()).increment();
        }
        if (e.origin() == AppointmentOrigin.REALLOCATED && facts.closeRelease(e.slotId(), REALLOCATED, clock.instant())) {
            meters.counter("vagaviva.slots.reallocated", "via", "QUEUE").increment();
        }
    }

    @Override
    @Transactional
    public void project(AppointmentConfirmed e) {
        if (facts.markConfirmed(e.appointmentId())) {
            outcome(AppointmentStatus.CONFIRMED);
        }
    }

    @Override
    @Transactional
    public void project(AppointmentAttended e) {
        close(e.appointmentId(), AppointmentStatus.ATTENDED);
    }

    @Override
    @Transactional
    public void project(AppointmentMissed e) {
        close(e.appointmentId(), AppointmentStatus.NO_SHOW);
    }

    @Override
    @Transactional
    public void project(AppointmentCancelled e) {
        close(e.appointmentId(), switch (e.reason()) {
            case UNIT -> AppointmentStatus.CANCELLED_BY_UNIT;
            case PATIENT -> AppointmentStatus.CANCELLED_BY_PATIENT;
            case WITHDRAWN -> AppointmentStatus.WITHDRAWN;
        });
    }

    @Override
    @Transactional
    public void project(AppointmentExpired e) {
        close(e.appointmentId(), AppointmentStatus.EXPIRED_UNCONFIRMED);
    }

    /** Liberada a menos de 2 h do início já nasce perdida (RN-15). */
    @Override
    @Transactional
    public void project(SlotReleased e) {
        UUID id = UUID.nameUUIDFromBytes((e.slotId() + ":" + e.releaseCount()).getBytes(StandardCharsets.UTF_8));
        String outcome = e.outcome() == SlotStatus.EXPIRED ? LOST : null;
        if (facts.insertRelease(id, e.slotId(), e.specialtyId(), e.unitId(), e.reason().name(), e.releasedAt(), outcome)) {
            meters.counter("vagaviva.slots.released", "reason", e.reason().name()).increment();
        }
    }

    @Override
    @Transactional
    public void project(SlotOfferAccepted e) {
        if (facts.closeRelease(e.slotId(), OFFER_ACCEPTED, clock.instant())) {
            meters.counter("vagaviva.slots.reallocated", "via", "OFFER").increment();
        }
    }

    /** Toda vaga perdida conta na métrica; o fato só existe se ela tinha sido liberada. */
    @Override
    @Transactional
    public void project(SlotLost e) {
        facts.closeRelease(e.slotId(), LOST, clock.instant());
        meters.counter("vagaviva.slots.lost").increment();
    }

    private void close(UUID appointmentId, AppointmentStatus status) {
        if (facts.closeAppointment(appointmentId, status.name(), clock.instant())) {
            outcome(status);
        }
    }

    private void outcome(AppointmentStatus status) {
        meters.counter("vagaviva.appointments.outcome", "status", status.name()).increment();
    }
}
