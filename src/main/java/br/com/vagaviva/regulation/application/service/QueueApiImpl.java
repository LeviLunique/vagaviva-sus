package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.ReferralView;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.regulation.ReviewReason;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada da fila para o agendamento. Chamadas de sistema (sem ator humano): a auditoria fica com o
 * módulo que executa a ação sobre o agendamento.
 */
@Service
class QueueApiImpl implements QueueApi {

    private final ReferralRepository repository;
    private final RegulationProperties properties;
    private final Clock clock;

    QueueApiImpl(ReferralRepository repository, RegulationProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReferralCandidate> lockNextEligible(UUID specialtyId, EligibilityCriteria criteria) {
        return repository.lockWaiting(specialtyId, criteria, false, Set.of(), 1).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReferralCandidate> lockShortNoticeCandidates(UUID specialtyId, EligibilityCriteria criteria,
            Set<UUID> excludedPatients, int limit) {
        return repository.lockWaiting(specialtyId, criteria, true, excludedPatients, limit);
    }

    @Override
    @Transactional
    public void markScheduled(UUID referralId) {
        change(referralId, referral -> referral.markScheduled(clock));
    }

    @Override
    @Transactional
    public void returnToQueue(UUID referralId, ReturnReason reason) {
        change(referralId, referral -> referral.returnToQueue(reason, properties.maxMissedConfirmations(), clock));
    }

    @Override
    @Transactional
    public void sendToReview(UUID referralId, ReviewReason reason) {
        change(referralId, referral -> referral.sendToReview(reason, clock));
    }

    @Override
    @Transactional
    public void markCompleted(UUID referralId) {
        change(referralId, referral -> referral.markCompleted(clock));
    }

    @Override
    @Transactional
    public void withdraw(UUID referralId) {
        change(referralId, referral -> referral.withdraw(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReferralView> findView(UUID referralId) {
        return repository.findById(referralId).map(referral -> new ReferralView(referral.id(),
                referral.protocol().value(), referral.patientId(), referral.specialtyId(), referral.requesterUnitId(),
                referral.status(), referral.riskClass(), referral.acceptsShortNotice(),
                referral.patientMunicipalityCode().value()));
    }

    private void change(UUID referralId, Consumer<Referral> transition) {
        Referral referral = repository.findById(referralId).orElseThrow(RegulationErrors::referralNotFound);
        transition.accept(referral);
        repository.save(referral);
    }
}
