package br.com.vagaviva.regulation.application.port.in;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Consultas dos profissionais: encaminhamentos (RN-03 para REQUESTER) e fila priorizada (RF-16). */
public interface QueryReferralsUseCase {

    /** Leitura auditada; REQUESTER só vê encaminhamentos da própria unidade. */
    Referral get(UUID referralId, CurrentUser actor, @Nullable String clientIp);

    /** REQUESTER: o filtro de unidade é sempre a unidade dele. */
    Page<Referral> list(ReferralFilter filter, Pageable pageable, CurrentUser actor);

    /** Fila da especialidade na ordem oficial, com paciente mascarado; leitura auditada. */
    Page<QueueItem> queue(UUID specialtyId, Pageable pageable, CurrentUser actor, @Nullable String clientIp);

    record ReferralFilter(@Nullable ReferralStatus status, @Nullable UUID specialtyId, @Nullable UUID requesterUnitId) {
    }

    record QueueItem(int position, UUID referralId, String protocol, RiskClass riskClass, boolean priorityGroup,
            Instant queueEnteredAt, long waitingDays, UUID patientId, @Nullable String patientFirstName,
            @Nullable String patientCnsMasked) {
    }
}
