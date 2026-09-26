package br.com.vagaviva.reallocation.adapter.out.persistence;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_offer")
class SlotOfferEntity {

    @Id
    private UUID id;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private short round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OfferStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SlotOfferEntity() {
    }

    static SlotOfferEntity from(SlotOffer offer) {
        SlotOfferEntity entity = new SlotOfferEntity();
        entity.id = offer.id();
        entity.slotId = offer.slotId();
        entity.referralId = offer.referralId();
        entity.patientId = offer.patientId();
        entity.round = (short) offer.round();
        entity.status = offer.status();
        entity.expiresAt = offer.expiresAt();
        entity.respondedAt = offer.respondedAt();
        entity.createdAt = offer.createdAt();
        return entity;
    }

    SlotOffer toDomain() {
        return SlotOffer.restore(id, slotId, referralId, patientId, round, status, expiresAt, respondedAt, createdAt);
    }
}
