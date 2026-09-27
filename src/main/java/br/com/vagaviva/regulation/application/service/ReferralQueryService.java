package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReferralQueryService implements QueryReferralsUseCase {

    private final ReferralRepository repository;
    private final PatientApi patients;
    private final CatalogApi catalog;
    private final RegulationAudit audit;
    private final Clock clock;

    ReferralQueryService(ReferralRepository repository, PatientApi patients, CatalogApi catalog, RegulationAudit audit,
            Clock clock) {
        this.repository = repository;
        this.patients = patients;
        this.catalog = catalog;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Referral get(UUID referralId, CurrentUser actor, @Nullable String clientIp) {
        Referral referral = repository.findById(referralId).orElseThrow(RegulationErrors::referralNotFound);
        if (actor.role() == Role.REQUESTER && !referral.belongsToUnit(actor.unitId())) {
            throw RegulationErrors.outOfUnit();
        }
        audit.record(actor, RegulationAudit.REFERRAL_READ, RegulationAudit.REFERRAL, referral.id(),
                AuditOutcome.SUCCESS, clientIp, Map.of());
        return referral;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Referral> list(ReferralFilter filter, Pageable pageable, CurrentUser actor) {
        ReferralFilter effective = actor.role() == Role.REQUESTER
                ? new ReferralFilter(filter.status(), filter.specialtyId(), actor.unitId())
                : filter;
        return repository.search(effective, pageable);
    }

    @Override
    @Transactional
    public Page<QueueItem> queue(UUID specialtyId, Pageable pageable, CurrentUser actor, @Nullable String clientIp) {
        catalog.findSpecialty(specialtyId).orElseThrow(RegulationErrors::specialtyNotFound);
        Page<Referral> page = repository.findQueue(specialtyId, pageable);
        Map<UUID, PatientSummary> summaries = patients.findSummaries(
                page.getContent().stream().map(Referral::patientId).toList());
        AtomicInteger position = new AtomicInteger((int) pageable.getOffset());
        Page<QueueItem> items = page.map(referral -> item(position.incrementAndGet(), referral,
                summaries.get(referral.patientId())));
        audit.record(actor, RegulationAudit.QUEUE_READ, RegulationAudit.QUEUE, specialtyId, AuditOutcome.SUCCESS,
                clientIp, Map.of("page", String.valueOf(pageable.getPageNumber())));
        return items;
    }

    private QueueItem item(int position, Referral referral, @Nullable PatientSummary patient) {
        long waitingDays = Duration.between(referral.queueEnteredAt(), clock.instant()).toDays();
        return new QueueItem(position, referral.id(), referral.protocol().value(), referral.riskClass(),
                referral.priorityGroup(), referral.queueEnteredAt(), waitingDays, referral.patientId(),
                patient == null ? null : patient.firstName(), patient == null ? null : patient.cnsMasked());
    }
}
