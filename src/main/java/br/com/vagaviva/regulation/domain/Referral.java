package br.com.vagaviva.regulation.domain;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.regulation.ReviewReason;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.Ids;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Encaminhamento de um paciente a uma especialidade (agregado da regulação). Toda mudança de
 * estado passa por {@link #moveTo(ReferralStatus)}, que aplica as transições do SPEC §5.2.
 */
public final class Referral {

    private static final Pattern CID10 = Pattern.compile("[A-Z]\\d{2}(\\.?[0-9A-Z]{1,4})?");

    private final UUID id;
    private final Protocol protocol;
    private final UUID patientId;
    private final UUID specialtyId;
    private final UUID requesterUnitId;
    private final UUID requestedBy;
    private String clinicalJustification;
    private @Nullable String cid10;
    private boolean acceptsShortNotice;
    private final MunicipalityCode patientMunicipalityCode;
    private boolean priorityGroup;
    private @Nullable RiskClass riskClass;
    private ReferralStatus status;
    private @Nullable Instant queueEnteredAt;
    private @Nullable UUID regulatedBy;
    private @Nullable Instant regulatedAt;
    private @Nullable String returnReason;
    private @Nullable String cancelReason;
    private int missedConfirmations;
    private int noShows;
    private @Nullable Instant scheduledAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final @Nullable Long version;

    private Referral(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.protocol = Objects.requireNonNull(b.protocol, "protocol");
        this.patientId = Objects.requireNonNull(b.patientId, "patientId");
        this.specialtyId = Objects.requireNonNull(b.specialtyId, "specialtyId");
        this.requesterUnitId = Objects.requireNonNull(b.requesterUnitId, "requesterUnitId");
        this.requestedBy = Objects.requireNonNull(b.requestedBy, "requestedBy");
        this.clinicalJustification = Objects.requireNonNull(b.clinicalJustification, "clinicalJustification");
        this.cid10 = b.cid10;
        this.acceptsShortNotice = b.acceptsShortNotice;
        this.patientMunicipalityCode = Objects.requireNonNull(b.patientMunicipalityCode, "patientMunicipalityCode");
        this.priorityGroup = b.priorityGroup;
        this.riskClass = b.riskClass;
        this.status = Objects.requireNonNull(b.status, "status");
        this.queueEnteredAt = b.queueEnteredAt;
        this.regulatedBy = b.regulatedBy;
        this.regulatedAt = b.regulatedAt;
        this.returnReason = b.returnReason;
        this.cancelReason = b.cancelReason;
        this.missedConfirmations = b.missedConfirmations;
        this.noShows = b.noShows;
        this.scheduledAt = b.scheduledAt;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
        this.version = b.version;
    }

    /** RF-12: novo encaminhamento aguardando regulação. */
    public static Referral create(Protocol protocol, UUID patientId, UUID specialtyId, UUID requesterUnitId,
            UUID requestedBy, String clinicalJustification, @Nullable String cid10, boolean acceptsShortNotice,
            MunicipalityCode patientMunicipalityCode, Clock clock) {
        Instant now = clock.instant();
        return new Referral(builder()
                .id(Ids.newId())
                .protocol(protocol)
                .patientId(patientId)
                .specialtyId(specialtyId)
                .requesterUnitId(requesterUnitId)
                .requestedBy(requestedBy)
                .clinicalJustification(requireJustification(clinicalJustification))
                .cid10(normalizeCid10(cid10))
                .acceptsShortNotice(acceptsShortNotice)
                .patientMunicipalityCode(patientMunicipalityCode)
                .status(ReferralStatus.PENDING_REGULATION)
                .createdAt(now)
                .updatedAt(now));
    }

    public static Referral restore(Builder data) {
        return new Referral(data);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** RF-13: aprovado com classe de risco ⇒ entra na fila agora. */
    public void approve(RiskClass risk, boolean patientIsPriorityGroup, UUID regulatorId, Clock clock) {
        moveTo(ReferralStatus.WAITING);
        Instant now = clock.instant();
        this.riskClass = Objects.requireNonNull(risk, "risk");
        this.priorityGroup = patientIsPriorityGroup;
        this.queueEnteredAt = now;
        this.regulatedBy = regulatorId;
        this.regulatedAt = now;
        this.returnReason = null;
        this.updatedAt = now;
    }

    /** RF-13: devolvido à UBS para correção, com justificativa. */
    public void returnForCorrection(String reason, UUID regulatorId, Clock clock) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("RETURN_REASON_REQUIRED", "Informe a justificativa da devolução.");
        }
        moveTo(ReferralStatus.RETURNED);
        Instant now = clock.instant();
        this.returnReason = reason.strip();
        this.regulatedBy = regulatorId;
        this.regulatedAt = now;
        this.updatedAt = now;
    }

    /** RF-14: a UBS corrige e reenvia para regulação. */
    public void resubmit(String clinicalJustification, @Nullable String cid10, boolean acceptsShortNotice, Clock clock) {
        moveTo(ReferralStatus.PENDING_REGULATION);
        this.clinicalJustification = requireJustification(clinicalJustification);
        this.cid10 = normalizeCid10(cid10);
        this.acceptsShortNotice = acceptsShortNotice;
        this.updatedAt = clock.instant();
    }

    /** RF-15: cancelamento administrativo (antes do agendamento). */
    public void cancel(String reason, Clock clock) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("CANCEL_REASON_REQUIRED", "Informe o motivo do cancelamento.");
        }
        moveTo(ReferralStatus.CANCELLED);
        this.cancelReason = reason.strip();
        this.updatedAt = clock.instant();
    }

    public void markScheduled(Clock clock) {
        moveTo(ReferralStatus.SCHEDULED);
        this.scheduledAt = clock.instant();
        this.updatedAt = this.scheduledAt;
    }

    /**
     * Volta para a fila preservando a data de entrada (RN-06). Não confirmação conta para a RN-13:
     * ao atingir {@code maxMissedConfirmations}, vai para reavaliação do regulador.
     */
    public void returnToQueue(ReturnReason reason, int maxMissedConfirmations, Clock clock) {
        if (reason == ReturnReason.UNCONFIRMED && missedConfirmations + 1 >= maxMissedConfirmations) {
            moveTo(ReferralStatus.PENDING_REGULATION);
            missedConfirmations++;
        } else {
            moveTo(ReferralStatus.WAITING);
            if (reason == ReturnReason.UNCONFIRMED) {
                missedConfirmations++;
            }
        }
        this.updatedAt = clock.instant();
    }

    /** RN-14: falta ⇒ reavaliação pelo regulador. */
    public void sendToReview(ReviewReason reason, Clock clock) {
        moveTo(ReferralStatus.PENDING_REGULATION);
        if (reason == ReviewReason.NO_SHOW) {
            noShows++;
        }
        this.updatedAt = clock.instant();
    }

    public void markCompleted(Clock clock) {
        moveTo(ReferralStatus.COMPLETED);
        this.updatedAt = clock.instant();
    }

    public void withdraw(Clock clock) {
        moveTo(ReferralStatus.WITHDRAWN);
        this.updatedAt = clock.instant();
    }

    public boolean belongsToUnit(@Nullable UUID unitId) {
        return requesterUnitId.equals(unitId);
    }

    public QueueEntry toQueueEntry() {
        if (status != ReferralStatus.WAITING) {
            throw new IllegalStateException("Somente encaminhamentos WAITING estão na fila");
        }
        return new QueueEntry(id, riskClass, priorityGroup, queueEnteredAt);
    }

    private void moveTo(ReferralStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("REFERRAL_INVALID_STATE",
                    "Operação não permitida para encaminhamento em %s.".formatted(status));
        }
        this.status = target;
    }

    private static String requireJustification(String justification) {
        if (justification == null || justification.isBlank()) {
            throw new BusinessRuleException("CLINICAL_JUSTIFICATION_REQUIRED", "Informe a justificativa clínica.");
        }
        return justification.strip();
    }

    private static @Nullable String normalizeCid10(@Nullable String cid10) {
        if (cid10 == null || cid10.isBlank()) {
            return null;
        }
        String normalized = cid10.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > 8 || !CID10.matcher(normalized).matches()) {
            throw new BusinessRuleException("INVALID_CID10", "CID-10 inválido (ex.: I10, M54.5).");
        }
        return normalized;
    }

    public UUID id() {
        return id;
    }

    public Protocol protocol() {
        return protocol;
    }

    public UUID patientId() {
        return patientId;
    }

    public UUID specialtyId() {
        return specialtyId;
    }

    public UUID requesterUnitId() {
        return requesterUnitId;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public String clinicalJustification() {
        return clinicalJustification;
    }

    public @Nullable String cid10() {
        return cid10;
    }

    public boolean acceptsShortNotice() {
        return acceptsShortNotice;
    }

    public MunicipalityCode patientMunicipalityCode() {
        return patientMunicipalityCode;
    }

    public boolean priorityGroup() {
        return priorityGroup;
    }

    public @Nullable RiskClass riskClass() {
        return riskClass;
    }

    public ReferralStatus status() {
        return status;
    }

    public @Nullable Instant queueEnteredAt() {
        return queueEnteredAt;
    }

    public @Nullable UUID regulatedBy() {
        return regulatedBy;
    }

    public @Nullable Instant regulatedAt() {
        return regulatedAt;
    }

    public @Nullable String returnReason() {
        return returnReason;
    }

    public @Nullable String cancelReason() {
        return cancelReason;
    }

    public int missedConfirmations() {
        return missedConfirmations;
    }

    public int noShows() {
        return noShows;
    }

    public @Nullable Instant scheduledAt() {
        return scheduledAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Long version() {
        return version;
    }

    /** Montagem do agregado (usada na criação e na reconstrução pela persistência). */
    public static final class Builder {

        private UUID id;
        private Protocol protocol;
        private UUID patientId;
        private UUID specialtyId;
        private UUID requesterUnitId;
        private UUID requestedBy;
        private String clinicalJustification;
        private String cid10;
        private boolean acceptsShortNotice;
        private MunicipalityCode patientMunicipalityCode;
        private boolean priorityGroup;
        private RiskClass riskClass;
        private ReferralStatus status;
        private Instant queueEnteredAt;
        private UUID regulatedBy;
        private Instant regulatedAt;
        private String returnReason;
        private String cancelReason;
        private int missedConfirmations;
        private int noShows;
        private Instant scheduledAt;
        private Instant createdAt;
        private Instant updatedAt;
        private Long version;

        private Builder() {
        }

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder protocol(Protocol v) { this.protocol = v; return this; }
        public Builder patientId(UUID v) { this.patientId = v; return this; }
        public Builder specialtyId(UUID v) { this.specialtyId = v; return this; }
        public Builder requesterUnitId(UUID v) { this.requesterUnitId = v; return this; }
        public Builder requestedBy(UUID v) { this.requestedBy = v; return this; }
        public Builder clinicalJustification(String v) { this.clinicalJustification = v; return this; }
        public Builder cid10(@Nullable String v) { this.cid10 = v; return this; }
        public Builder acceptsShortNotice(boolean v) { this.acceptsShortNotice = v; return this; }
        public Builder patientMunicipalityCode(MunicipalityCode v) { this.patientMunicipalityCode = v; return this; }
        public Builder priorityGroup(boolean v) { this.priorityGroup = v; return this; }
        public Builder riskClass(@Nullable RiskClass v) { this.riskClass = v; return this; }
        public Builder status(ReferralStatus v) { this.status = v; return this; }
        public Builder queueEnteredAt(@Nullable Instant v) { this.queueEnteredAt = v; return this; }
        public Builder regulatedBy(@Nullable UUID v) { this.regulatedBy = v; return this; }
        public Builder regulatedAt(@Nullable Instant v) { this.regulatedAt = v; return this; }
        public Builder returnReason(@Nullable String v) { this.returnReason = v; return this; }
        public Builder cancelReason(@Nullable String v) { this.cancelReason = v; return this; }
        public Builder missedConfirmations(int v) { this.missedConfirmations = v; return this; }
        public Builder noShows(int v) { this.noShows = v; return this; }
        public Builder scheduledAt(@Nullable Instant v) { this.scheduledAt = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public Builder version(@Nullable Long v) { this.version = v; return this; }
    }
}
