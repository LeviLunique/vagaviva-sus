package br.com.vagaviva.regulation.application.port.in;

import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Ciclo de vida do encaminhamento pelos profissionais (RF-12 a RF-15). */
public interface ReferralCommandUseCases {

    /** RF-12 (REQUESTER, pela própria UBS). */
    Referral create(CreateReferralCommand command);

    /** RF-14 (REQUESTER da unidade de origem): corrige um encaminhamento devolvido. */
    Referral resubmit(ResubmitReferralCommand command);

    /** RF-13 (REGULATOR). */
    Referral regulate(RegulateReferralCommand command);

    /** RF-15 (REGULATOR, ADMIN). */
    Referral cancel(UUID referralId, String reason, CurrentUser actor, @Nullable String clientIp);

    record CreateReferralCommand(UUID patientId, UUID specialtyId, String clinicalJustification, @Nullable String cid10,
            boolean acceptsShortNotice, CurrentUser actor, @Nullable String clientIp) {
    }

    record ResubmitReferralCommand(UUID referralId, String clinicalJustification, @Nullable String cid10,
            boolean acceptsShortNotice, CurrentUser actor, @Nullable String clientIp) {
    }

    enum RegulationDecision {
        APPROVE,
        RETURN
    }

    record RegulateReferralCommand(UUID referralId, RegulationDecision decision, @Nullable RiskClass riskClass,
            @Nullable String justification, CurrentUser actor, @Nullable String clientIp) {
    }
}
