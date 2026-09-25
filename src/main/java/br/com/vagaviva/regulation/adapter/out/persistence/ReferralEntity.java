package br.com.vagaviva.regulation.adapter.out.persistence;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral")
class ReferralEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String protocol;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;

    @Column(name = "requester_unit_id", nullable = false)
    private UUID requesterUnitId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "clinical_justification", nullable = false, length = 2000)
    private String clinicalJustification;

    @Column(length = 8)
    private String cid10;

    @Column(name = "accepts_short_notice", nullable = false)
    private boolean acceptsShortNotice;

    @Column(name = "patient_municipality_code", nullable = false, length = 7)
    private String patientMunicipalityCode;

    @Column(name = "priority_group", nullable = false)
    private boolean priorityGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_class", length = 10)
    private RiskClass riskClass;

    /** Espelho numérico de {@code risk_class} para a ordenação e o índice da fila. */
    @Column(name = "risk_rank")
    private Short riskRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReferralStatus status;

    @Column(name = "queue_entered_at")
    private Instant queueEnteredAt;

    @Column(name = "regulated_by")
    private UUID regulatedBy;

    @Column(name = "regulated_at")
    private Instant regulatedAt;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "missed_confirmations", nullable = false)
    private short missedConfirmations;

    @Column(name = "no_shows", nullable = false)
    private short noShows;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ReferralEntity() {
    }

    static ReferralEntity from(Referral referral) {
        var entity = new ReferralEntity();
        entity.id = referral.id();
        entity.protocol = referral.protocol().value();
        entity.patientId = referral.patientId();
        entity.specialtyId = referral.specialtyId();
        entity.requesterUnitId = referral.requesterUnitId();
        entity.requestedBy = referral.requestedBy();
        entity.clinicalJustification = referral.clinicalJustification();
        entity.cid10 = referral.cid10();
        entity.acceptsShortNotice = referral.acceptsShortNotice();
        entity.patientMunicipalityCode = referral.patientMunicipalityCode().value();
        entity.priorityGroup = referral.priorityGroup();
        entity.riskClass = referral.riskClass();
        entity.riskRank = referral.riskClass() == null ? null : (short) referral.riskClass().rank();
        entity.status = referral.status();
        entity.queueEnteredAt = referral.queueEnteredAt();
        entity.regulatedBy = referral.regulatedBy();
        entity.regulatedAt = referral.regulatedAt();
        entity.returnReason = referral.returnReason();
        entity.cancelReason = referral.cancelReason();
        entity.missedConfirmations = (short) referral.missedConfirmations();
        entity.noShows = (short) referral.noShows();
        entity.scheduledAt = referral.scheduledAt();
        entity.createdAt = referral.createdAt();
        entity.updatedAt = referral.updatedAt();
        entity.version = referral.version();
        return entity;
    }

    Referral toDomain() {
        return Referral.restore(Referral.builder()
                .id(id)
                .protocol(new Protocol(protocol))
                .patientId(patientId)
                .specialtyId(specialtyId)
                .requesterUnitId(requesterUnitId)
                .requestedBy(requestedBy)
                .clinicalJustification(clinicalJustification)
                .cid10(cid10)
                .acceptsShortNotice(acceptsShortNotice)
                .patientMunicipalityCode(MunicipalityCode.of(patientMunicipalityCode))
                .priorityGroup(priorityGroup)
                .riskClass(riskClass)
                .status(status)
                .queueEnteredAt(queueEnteredAt)
                .regulatedBy(regulatedBy)
                .regulatedAt(regulatedAt)
                .returnReason(returnReason)
                .cancelReason(cancelReason)
                .missedConfirmations(missedConfirmations)
                .noShows(noShows)
                .scheduledAt(scheduledAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version));
    }
}
