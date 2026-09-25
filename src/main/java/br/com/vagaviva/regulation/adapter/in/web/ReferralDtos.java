package br.com.vagaviva.regulation.adapter.in.web;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.RegulationDecision;
import br.com.vagaviva.regulation.domain.Referral;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** DTOs de encaminhamentos. */
final class ReferralDtos {

    private ReferralDtos() {
    }

    record CreateReferralRequest(
            @NotNull UUID patientId,
            @NotNull UUID specialtyId,
            @Schema(example = "Dor torácica aos esforços há 3 meses, ECG com alteração de ST.")
            @NotBlank @Size(max = 2000) String clinicalJustification,
            @Schema(description = "Opcional", example = "I20.9") @Size(max = 8) String cid10,
            @Schema(description = "Aceita encaixe de última hora (padrão false)") Boolean acceptsShortNotice) {
    }

    record ResubmitReferralRequest(
            @NotBlank @Size(max = 2000) String clinicalJustification,
            @Size(max = 8) String cid10,
            Boolean acceptsShortNotice) {
    }

    record RegulationRequest(
            @NotNull RegulationDecision decision,
            @Schema(description = "Obrigatória para APPROVE") RiskClass riskClass,
            @Schema(description = "Obrigatória para RETURN") @Size(max = 500) String justification) {
    }

    record CancelReferralRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** Detalhe (profissional autorizado, leitura auditada): inclui os dados clínicos. */
    record ReferralResponse(UUID id, String protocol, UUID patientId, UUID specialtyId, UUID requesterUnitId,
            ReferralStatus status, RiskClass riskClass, boolean priorityGroup, String clinicalJustification,
            String cid10, boolean acceptsShortNotice, Instant queueEnteredAt, Instant regulatedAt, String returnReason,
            String cancelReason, int missedConfirmations, int noShows, Instant createdAt) {

        static ReferralResponse from(Referral r) {
            return new ReferralResponse(r.id(), r.protocol().value(), r.patientId(), r.specialtyId(),
                    r.requesterUnitId(), r.status(), r.riskClass(), r.priorityGroup(), r.clinicalJustification(),
                    r.cid10(), r.acceptsShortNotice(), r.queueEnteredAt(), r.regulatedAt(), r.returnReason(),
                    r.cancelReason(), r.missedConfirmations(), r.noShows(), r.createdAt());
        }
    }

    /** Item de listagem: sem justificativa clínica nem CID (minimização). */
    record ReferralSummaryResponse(UUID id, String protocol, UUID patientId, UUID specialtyId, UUID requesterUnitId,
            ReferralStatus status, RiskClass riskClass, boolean priorityGroup, Instant queueEnteredAt,
            Instant createdAt) {

        static ReferralSummaryResponse from(Referral r) {
            return new ReferralSummaryResponse(r.id(), r.protocol().value(), r.patientId(), r.specialtyId(),
                    r.requesterUnitId(), r.status(), r.riskClass(), r.priorityGroup(), r.queueEnteredAt(),
                    r.createdAt());
        }
    }

    record CreatedReferralResponse(UUID id, String protocol, ReferralStatus status) {
    }
}
