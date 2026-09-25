package br.com.vagaviva.regulation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API da fila para o agendamento (F4) e o encaixe (F6). Os métodos {@code lock*} exigem uma
 * transação aberta por quem chama: as linhas ficam travadas ({@code FOR UPDATE SKIP LOCKED}) até
 * o commit, o que permite várias instâncias alocando em paralelo sem disputar o mesmo paciente.
 */
public interface QueueApi {

    /** Próximo elegível na ordem oficial (RN-06, RN-10), ou vazio. */
    Optional<ReferralCandidate> lockNextEligible(UUID specialtyId, EligibilityCriteria criteria);

    /** Candidatos a encaixe de última hora ({@code acceptsShortNotice}), na ordem oficial. */
    List<ReferralCandidate> lockShortNoticeCandidates(UUID specialtyId, EligibilityCriteria criteria,
            Set<UUID> excludedPatients, int limit);

    void markScheduled(UUID referralId);

    /** Volta à fila preservando a data de entrada; {@code UNCONFIRMED} aplica a RN-13. */
    void returnToQueue(UUID referralId, ReturnReason reason);

    void sendToReview(UUID referralId, ReviewReason reason);

    void markCompleted(UUID referralId);

    void withdraw(UUID referralId);

    Optional<ReferralView> findView(UUID referralId);
}
