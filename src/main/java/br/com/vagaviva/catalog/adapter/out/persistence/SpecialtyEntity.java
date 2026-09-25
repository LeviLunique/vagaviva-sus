package br.com.vagaviva.catalog.adapter.out.persistence;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.domain.Specialty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "specialty")
class SpecialtyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpecialtyType type;

    @Column(nullable = false)
    private boolean sensitive;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SpecialtyEntity() {
    }

    static SpecialtyEntity from(Specialty specialty) {
        var entity = new SpecialtyEntity();
        entity.id = specialty.id();
        entity.code = specialty.code();
        entity.name = specialty.name();
        entity.type = specialty.type();
        entity.sensitive = specialty.sensitive();
        entity.active = specialty.active();
        entity.createdAt = specialty.createdAt();
        return entity;
    }

    Specialty toDomain() {
        return new Specialty(id, code, name, type, sensitive, active, createdAt);
    }
}
