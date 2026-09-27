package br.com.vagaviva.regulation;

import java.time.Instant;
import java.util.UUID;

/** Encaminhamento travado para alocação (linha bloqueada até o fim da transação de quem chamou). */
public record ReferralCandidate(UUID referralId, UUID patientId, UUID specialtyId, RiskClass riskClass,
        boolean priorityGroup, Instant queueEnteredAt, boolean acceptsShortNotice) {
}
