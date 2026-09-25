package br.com.vagaviva.regulation;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Visão do encaminhamento para outros módulos — sem justificativa clínica nem CID (dados sensíveis). */
public record ReferralView(UUID id, String protocol, UUID patientId, UUID specialtyId, UUID requesterUnitId,
        ReferralStatus status, @Nullable RiskClass riskClass, boolean acceptsShortNotice, String patientMunicipalityCode) {
}
