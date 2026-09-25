package br.com.vagaviva.regulation.application.port.out;

import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.ReferralFilter;
import br.com.vagaviva.regulation.domain.Referral;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReferralRepository {

    /** @throws br.com.vagaviva.shared.domain.ConflictException {@code REFERRAL_DUPLICATED} (índice de ativos) */
    Referral save(Referral referral);

    Optional<Referral> findById(UUID id);

    Optional<Referral> findByProtocol(String protocol);

    /** RN-05: já existe encaminhamento ativo do paciente para a especialidade? */
    boolean existsActive(UUID patientId, UUID specialtyId);

    /** Mais recentes primeiro. */
    Page<Referral> search(ReferralFilter filter, Pageable pageable);

    /** Encaminhamentos {@code WAITING} da especialidade na ordem oficial (RN-06). */
    Page<Referral> findQueue(UUID specialtyId, Pageable pageable);

    /** Linhas travadas com {@code FOR UPDATE SKIP LOCKED} na ordem oficial. */
    List<ReferralCandidate> lockWaiting(UUID specialtyId, EligibilityCriteria criteria, boolean shortNoticeOnly,
            Set<UUID> excludedPatients, int limit);
}
