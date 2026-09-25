package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.ConfirmationPolicy;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Motor de alocação (RF-20). Cada vaga é alocada numa transação própria: a vaga é travada com
 * {@code SKIP LOCKED} e o próximo elegível da fila também — várias instâncias (ou o job e o evento
 * de publicação ao mesmo tempo) trabalham em paralelo sem pegar a mesma vaga nem o mesmo
 * paciente, e a falha de uma vaga não desfaz as outras.
 */
@Service
class AllocationService implements RunAllocationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final QueueApi queue;
    private final CatalogApi catalog;
    private final ConfirmationPolicy confirmationPolicy;
    private final SchedulingProperties properties;
    private final SchedulingAudit audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate perSlotTransaction;
    private final Clock clock;

    AllocationService(SlotRepository slots, AppointmentRepository appointments, QueueApi queue, CatalogApi catalog,
            ConfirmationPolicy confirmationPolicy, SchedulingProperties properties, SchedulingAudit audit,
            ApplicationEventPublisher events, PlatformTransactionManager transactionManager, Clock clock) {
        this.slots = slots;
        this.appointments = appointments;
        this.queue = queue;
        this.catalog = catalog;
        this.confirmationPolicy = confirmationPolicy;
        this.properties = properties;
        this.audit = audit;
        this.events = events;
        this.perSlotTransaction = new TransactionTemplate(transactionManager);
        this.perSlotTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public AllocationRunResult run() {
        List<UUID> candidates = slots.findAllocatableIds(clock.instant().plus(properties.regularAllocationMinLead()),
                properties.allocationBatchSize());
        int examined = 0;
        int allocated = 0;
        for (UUID slotId : candidates) {
            try {
                Outcome outcome = perSlotTransaction.execute(status -> allocate(slotId));
                if (outcome != Outcome.SKIPPED) {
                    examined++;
                }
                if (outcome == Outcome.ALLOCATED) {
                    allocated++;
                }
            } catch (RuntimeException ex) {
                log.warn("Falha ao alocar a vaga {}: {}", slotId, ex.getMessage());
            }
        }
        if (allocated > 0) {
            log.info("Alocação: {} vaga(s) avaliada(s), {} agendamento(s) criado(s).", examined, allocated);
        }
        return new AllocationRunResult(examined, allocated, examined - allocated);
    }

    private Outcome allocate(UUID slotId) {
        Optional<Slot> locked = slots.lockAvailable(slotId);
        if (locked.isEmpty()) {
            return Outcome.SKIPPED;
        }
        Slot slot = locked.get();
        EligibilityCriteria criteria = new EligibilityCriteria(catalog.findUnit(slot.unitId())
                .map(HealthUnitSummary::serviceArea).orElseThrow());
        Optional<ReferralCandidate> candidate = queue.lockNextEligible(slot.specialtyId(), criteria);
        if (candidate.isEmpty()) {
            return Outcome.NO_CANDIDATE;
        }
        ReferralCandidate referral = candidate.get();
        slot.allocate(clock);
        slots.save(slot);
        Appointment appointment = appointments.save(Appointment.schedule(slot, referral.referralId(),
                referral.patientId(), confirmationPolicy, clock));
        queue.markScheduled(referral.referralId());
        events.publishEvent(new AppointmentScheduled(appointment.id(), slot.id(), referral.referralId(),
                referral.patientId(), slot.unitId(), slot.specialtyId(), slot.startAt(), appointment.origin(),
                appointment.confirmationDeadline(), referral.queueEnteredAt()));
        audit.record(null, SchedulingAudit.APPOINTMENT_SCHEDULED, SchedulingAudit.APPOINTMENT, appointment.id(), null,
                Map.of("slotId", slot.id().toString(), "referralId", referral.referralId().toString()));
        return Outcome.ALLOCATED;
    }

    private enum Outcome {
        /** Outra transação já travou ou alocou a vaga. */
        SKIPPED,
        NO_CANDIDATE,
        ALLOCATED
    }
}
