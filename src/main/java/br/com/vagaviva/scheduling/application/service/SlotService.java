package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.domain.SlotLeadTimes;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.SlotOpenedForOffers;
import br.com.vagaviva.scheduling.events.SlotsPublished;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SlotService implements SlotUseCases {

    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final CatalogApi catalog;
    private final QueueApi queue;
    private final SchedulingAudit audit;
    private final ApplicationEventPublisher events;
    private final SlotLeadTimes leadTimes;
    private final Clock clock;

    SlotService(SlotRepository slots, AppointmentRepository appointments, CatalogApi catalog, QueueApi queue,
            SchedulingAudit audit, ApplicationEventPublisher events, SlotLeadTimes leadTimes, Clock clock) {
        this.slots = slots;
        this.appointments = appointments;
        this.catalog = catalog;
        this.queue = queue;
        this.audit = audit;
        this.events = events;
        this.leadTimes = leadTimes;
        this.clock = clock;
    }

    /** RF-19: lote inteiro válido ou nada é publicado (mesma transação). */
    @Override
    @Transactional
    public List<Slot> publish(PublishSlotsCommand command) {
        UnitScope.requireAccess(command.actor(), command.unitId());
        catalog.findUnit(command.unitId())
                .filter(HealthUnitSummary::active)
                .filter(unit -> unit.type() == HealthUnitType.SPECIALIZED)
                .orElseThrow(SchedulingErrors::invalidUnit);
        catalog.findSpecialty(command.specialtyId()).filter(SpecialtySummary::active)
                .orElseThrow(SchedulingErrors::invalidSpecialty);

        List<Slot> batch = command.slots().stream()
                .map(time -> Slot.publish(command.unitId(), command.specialtyId(), command.professionalName(),
                        time.startAt(), time.durationMinutes(), leadTimes, clock))
                .sorted(Comparator.comparing(Slot::startAt))
                .toList();
        rejectOverlaps(batch);
        List<Slot> saved = slots.saveAll(batch);

        saved.stream().filter(slot -> slot.status() == SlotStatus.OPEN_FOR_OFFERS)
                .forEach(slot -> events.publishEvent(new SlotOpenedForOffers(slot.id(), slot.unitId(),
                        slot.specialtyId(), slot.startAt())));
        events.publishEvent(new SlotsPublished(command.unitId(), command.specialtyId(), saved.size()));
        audit.record(command.actor(), SchedulingAudit.SLOTS_PUBLISHED, SchedulingAudit.SLOT, null, command.clientIp(),
                Map.of("unitId", command.unitId().toString(), "count", String.valueOf(saved.size())));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Slot> list(SlotFilter filter, Pageable pageable, CurrentUser actor) {
        SlotFilter effective = new SlotFilter(UnitScope.effectiveUnit(actor, filter.unitId()), filter.specialtyId(),
                filter.from(), filter.to(), filter.status());
        return slots.search(effective, pageable);
    }

    @Override
    @Transactional
    public Slot cancel(UUID slotId, String reason, CurrentUser actor, @Nullable String clientIp) {
        Slot slot = slots.findById(slotId).orElseThrow(SchedulingErrors::slotNotFound);
        UnitScope.requireAccess(actor, slot.unitId());
        slot.cancel(clock);
        Slot saved = slots.save(slot);
        appointments.findOpenBySlot(slotId).ifPresent(appointment -> cancelAppointment(appointment, actor, clientIp));
        audit.record(actor, SchedulingAudit.SLOT_CANCELLED, SchedulingAudit.SLOT, slotId, clientIp,
                Map.of("reason", reason.strip()));
        return saved;
    }

    /** RF-23: o paciente volta à fila na mesma posição (motivo não imputável a ele). */
    private void cancelAppointment(Appointment appointment, CurrentUser actor, @Nullable String clientIp) {
        appointment.cancelByUnit(clock);
        appointments.save(appointment);
        queue.returnToQueue(appointment.referralId(), ReturnReason.UNIT_CANCELLED);
        events.publishEvent(new AppointmentCancelled(appointment.id(), appointment.slotId(), appointment.referralId(),
                appointment.patientId(), appointment.startAt(), AppointmentCancelled.Reason.UNIT));
        audit.record(actor, SchedulingAudit.APPOINTMENT_CANCELLED_BY_UNIT, SchedulingAudit.APPOINTMENT,
                appointment.id(), clientIp, Map.of());
    }

    private void rejectOverlaps(List<Slot> batch) {
        List<Slot> accepted = new ArrayList<>();
        for (Slot slot : batch) {
            boolean clashesInBatch = accepted.stream().anyMatch(slot::overlaps);
            if (clashesInBatch || slots.existsOverlap(slot.unitId(), slot.professionalName(), slot.startAt(), slot.endAt())) {
                throw SchedulingErrors.overlap();
            }
            accepted.add(slot);
        }
    }
}
