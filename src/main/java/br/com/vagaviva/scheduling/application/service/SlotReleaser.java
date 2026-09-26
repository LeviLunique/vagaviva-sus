package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.ReleasePolicy;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotOpenedForOffers;
import br.com.vagaviva.scheduling.events.SlotReleased;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * RF-30 / RN-15: toda vaga liberada (cancelamento do paciente, desistência, prazo vencido) passa por
 * aqui — volta à alocação regular, vira oferta de encaixe (F6) ou é registrada como perdida.
 */
@Component
class SlotReleaser {

    private final SlotRepository slots;
    private final ReleasePolicy policy;
    private final SchedulingAudit audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SlotReleaser(SlotRepository slots, ReleasePolicy policy, SchedulingAudit audit, ApplicationEventPublisher events,
            Clock clock) {
        this.slots = slots;
        this.policy = policy;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    SlotStatus release(UUID slotId, SlotReleased.Reason reason) {
        Slot slot = slots.findById(slotId).orElseThrow(SchedulingErrors::slotOfAppointmentMissing);
        SlotStatus destination = policy.decide(slot.startAt(), clock.instant());
        slot.release(destination, clock);
        slots.save(slot);
        events.publishEvent(new SlotReleased(slot.id(), reason, destination, slot.startAt()));
        if (destination == SlotStatus.OPEN_FOR_OFFERS) {
            events.publishEvent(new SlotOpenedForOffers(slot.id(), slot.unitId(), slot.specialtyId(), slot.startAt()));
        } else if (destination == SlotStatus.EXPIRED) {
            events.publishEvent(new SlotLost(slot.id(), slot.unitId(), slot.specialtyId(), slot.startAt()));
        }
        audit.record(null, SchedulingAudit.SLOT_RELEASED, SchedulingAudit.SLOT, slot.id(), null,
                Map.of("reason", reason.name(), "outcome", destination.name()));
        return destination;
    }
}
