package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comandos sobre o agregado {@link Referral}: criar, reenviar, regular e cancelar. */
@Service
class ReferralCommandService implements ReferralCommandUseCases {

    private final ReferralRepository repository;
    private final ProtocolGenerator protocols;
    private final PatientApi patients;
    private final CatalogApi catalog;
    private final RegulationAudit audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReferralCommandService(ReferralRepository repository, ProtocolGenerator protocols, PatientApi patients,
            CatalogApi catalog, RegulationAudit audit, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.protocols = protocols;
        this.patients = patients;
        this.catalog = catalog;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Referral create(CreateReferralCommand command) {
        UUID unitId = command.actor().unitId();
        if (unitId == null) {
            throw RegulationErrors.unitRequired();
        }
        PatientSummary patient = patients.findSummary(command.patientId()).filter(PatientSummary::active)
                .orElseThrow(RegulationErrors::invalidPatient);
        catalog.findSpecialty(command.specialtyId()).filter(SpecialtySummary::active)
                .orElseThrow(RegulationErrors::invalidSpecialty);
        if (repository.existsActive(command.patientId(), command.specialtyId())) {
            throw RegulationErrors.duplicated();
        }
        Referral referral = repository.save(Referral.create(protocols.next(), command.patientId(),
                command.specialtyId(), unitId, command.actor().id(), command.clinicalJustification(), command.cid10(),
                command.acceptsShortNotice(), MunicipalityCode.of(patient.municipalityCode()), clock));
        audit.record(command.actor(), RegulationAudit.REFERRAL_CREATED, RegulationAudit.REFERRAL, referral.id(),
                AuditOutcome.SUCCESS, command.clientIp(), Map.of("specialtyId", command.specialtyId().toString()));
        return referral;
    }

    @Override
    @Transactional
    public Referral resubmit(ResubmitReferralCommand command) {
        Referral referral = load(command.referralId());
        if (!referral.belongsToUnit(command.actor().unitId())) {
            throw RegulationErrors.outOfUnit();
        }
        referral.resubmit(command.clinicalJustification(), command.cid10(), command.acceptsShortNotice(), clock);
        Referral saved = repository.save(referral);
        audit.record(command.actor(), RegulationAudit.REFERRAL_RESUBMITTED, RegulationAudit.REFERRAL, saved.id(),
                AuditOutcome.SUCCESS, command.clientIp(), Map.of());
        return saved;
    }

    @Override
    @Transactional
    public Referral regulate(RegulateReferralCommand command) {
        Referral referral = load(command.referralId());
        if (command.decision() == RegulationDecision.APPROVE) {
            if (command.riskClass() == null) {
                throw RegulationErrors.riskClassRequired();
            }
            boolean priorityGroup = patients.findSummary(referral.patientId())
                    .map(PatientSummary::priorityGroup).orElse(false);
            referral.approve(command.riskClass(), priorityGroup, command.actor().id(), clock);
        } else {
            referral.returnForCorrection(command.justification(), command.actor().id(), clock);
        }
        Referral saved = repository.save(referral);
        audit.record(command.actor(), RegulationAudit.REFERRAL_REGULATED, RegulationAudit.REFERRAL, saved.id(),
                AuditOutcome.SUCCESS, command.clientIp(), decisionDetails(command));
        if (command.decision() == RegulationDecision.APPROVE) {
            events.publishEvent(new ReferralQueued(saved.id(), saved.patientId(), saved.protocol().value()));
        }
        return saved;
    }

    @Override
    @Transactional
    public Referral cancel(UUID referralId, String reason, CurrentUser actor, @Nullable String clientIp) {
        Referral referral = load(referralId);
        referral.cancel(reason, clock);
        Referral saved = repository.save(referral);
        audit.record(actor, RegulationAudit.REFERRAL_CANCELLED, RegulationAudit.REFERRAL, saved.id(),
                AuditOutcome.SUCCESS, clientIp, Map.of());
        return saved;
    }

    private Referral load(UUID referralId) {
        return repository.findById(referralId).orElseThrow(RegulationErrors::referralNotFound);
    }

    private static Map<String, String> decisionDetails(RegulateReferralCommand command) {
        return command.riskClass() == null || command.decision() == RegulationDecision.RETURN
                ? Map.of("decision", command.decision().name())
                : Map.of("decision", command.decision().name(), "riskClass", command.riskClass().name());
    }
}
