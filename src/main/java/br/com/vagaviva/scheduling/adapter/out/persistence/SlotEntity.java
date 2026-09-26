package br.com.vagaviva.scheduling.adapter.out.persistence;

import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.domain.Slot;
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
@Table(name = "slot")
class SlotEntity {

    @Id
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;

    @Column(name = "professional_name", nullable = false, length = 120)
    private String professionalName;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status;

    @Column(name = "release_count", nullable = false)
    private short releaseCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected SlotEntity() {
    }

    static SlotEntity from(Slot slot) {
        var entity = new SlotEntity();
        entity.id = slot.id();
        entity.unitId = slot.unitId();
        entity.specialtyId = slot.specialtyId();
        entity.professionalName = slot.professionalName();
        entity.startAt = slot.startAt();
        entity.durationMinutes = (short) slot.durationMinutes();
        entity.status = slot.status();
        entity.releaseCount = (short) slot.releaseCount();
        entity.createdAt = slot.createdAt();
        entity.updatedAt = slot.updatedAt();
        entity.version = slot.version();
        return entity;
    }

    Slot toDomain() {
        return Slot.restore(id, unitId, specialtyId, professionalName, startAt, durationMinutes, status, releaseCount,
                createdAt, updatedAt, version);
    }
}
