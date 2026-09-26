package br.com.vagaviva.engagement.adapter.out.persistence;

import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.engagement.domain.TokenPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_action_token")
class PatientActionTokenEntity {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TokenPurpose purpose;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected PatientActionTokenEntity() {
    }

    static PatientActionTokenEntity from(PatientActionToken token) {
        var entity = new PatientActionTokenEntity();
        entity.id = token.id();
        entity.tokenHash = token.tokenHash();
        entity.purpose = token.purpose();
        entity.subjectId = token.subjectId();
        entity.expiresAt = token.expiresAt();
        entity.createdAt = token.createdAt();
        entity.lastUsedAt = token.lastUsedAt();
        return entity;
    }

    PatientActionToken toDomain() {
        return PatientActionToken.restore(id, tokenHash, purpose, subjectId, expiresAt, createdAt, lastUsedAt);
    }
}
