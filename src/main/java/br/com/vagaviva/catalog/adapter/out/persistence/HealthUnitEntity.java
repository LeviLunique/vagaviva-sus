package br.com.vagaviva.catalog.adapter.out.persistence;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "health_unit")
class HealthUnitEntity {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7)
    private String cnes;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HealthUnitType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "municipality_code", nullable = false, length = 7)
    private String municipalityCode;

    @Column(name = "municipality_name", nullable = false, length = 80)
    private String municipalityName;

    @Column(nullable = false, length = 200)
    private String address;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "health_unit_service_area", joinColumns = @JoinColumn(name = "health_unit_id"))
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "municipality_code", length = 7)
    private Set<String> serviceArea = new HashSet<>();

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected HealthUnitEntity() {
    }

    static HealthUnitEntity from(HealthUnit unit) {
        var entity = new HealthUnitEntity();
        entity.id = unit.id();
        entity.cnes = unit.cnes().value();
        entity.name = unit.name();
        entity.type = unit.type();
        entity.municipalityCode = unit.municipalityCode().value();
        entity.municipalityName = unit.municipalityName();
        entity.address = unit.address();
        entity.serviceArea = unit.serviceArea().stream().map(MunicipalityCode::value)
                .collect(Collectors.toCollection(HashSet::new));
        entity.active = unit.isActive();
        entity.createdAt = unit.createdAt();
        entity.updatedAt = unit.updatedAt();
        entity.version = unit.version();
        return entity;
    }

    HealthUnit toDomain() {
        return HealthUnit.restore(id, Cnes.of(cnes), name, type, MunicipalityCode.of(municipalityCode),
                municipalityName, address, serviceArea.stream().map(MunicipalityCode::of).collect(Collectors.toSet()),
                active, createdAt, updatedAt, version);
    }
}
