package br.com.vagaviva.catalog.domain;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.shared.domain.Ids;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Unidade de saúde (RF-06). A área de atendimento vazia significa "atende todos os municípios". */
public final class HealthUnit {

    private final UUID id;
    private final Cnes cnes;
    private final String name;
    private final HealthUnitType type;
    private final MunicipalityCode municipalityCode;
    private final String municipalityName;
    private final String address;
    private Set<MunicipalityCode> serviceArea;
    private final boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final @Nullable Long version;

    private HealthUnit(UUID id, Cnes cnes, String name, HealthUnitType type, MunicipalityCode municipalityCode,
            String municipalityName, String address, Set<MunicipalityCode> serviceArea, boolean active,
            Instant createdAt, Instant updatedAt, @Nullable Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.cnes = Objects.requireNonNull(cnes, "cnes");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.municipalityCode = Objects.requireNonNull(municipalityCode, "municipalityCode");
        this.municipalityName = Objects.requireNonNull(municipalityName, "municipalityName");
        this.address = Objects.requireNonNull(address, "address");
        this.serviceArea = Set.copyOf(serviceArea);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static HealthUnit register(Cnes cnes, String name, HealthUnitType type, MunicipalityCode municipalityCode,
            String municipalityName, String address, Set<MunicipalityCode> serviceArea, Clock clock) {
        Instant now = clock.instant();
        return new HealthUnit(Ids.newId(), cnes, name.strip(), type, municipalityCode, municipalityName.strip(),
                address.strip(), serviceArea, true, now, now, null);
    }

    public static HealthUnit restore(UUID id, Cnes cnes, String name, HealthUnitType type,
            MunicipalityCode municipalityCode, String municipalityName, String address,
            Set<MunicipalityCode> serviceArea, boolean active, Instant createdAt, Instant updatedAt,
            @Nullable Long version) {
        return new HealthUnit(id, cnes, name, type, municipalityCode, municipalityName, address, serviceArea, active,
                createdAt, updatedAt, version);
    }

    /** Substitui a área de atendimento inteira (PUT idempotente). */
    public void replaceServiceArea(Set<MunicipalityCode> municipalities, Clock clock) {
        this.serviceArea = Set.copyOf(municipalities);
        this.updatedAt = clock.instant();
    }

    public boolean serves(MunicipalityCode municipality) {
        return serviceArea.isEmpty() || serviceArea.contains(municipality);
    }

    public UUID id() {
        return id;
    }

    public Cnes cnes() {
        return cnes;
    }

    public String name() {
        return name;
    }

    public HealthUnitType type() {
        return type;
    }

    public MunicipalityCode municipalityCode() {
        return municipalityCode;
    }

    public String municipalityName() {
        return municipalityName;
    }

    public String address() {
        return address;
    }

    public Set<MunicipalityCode> serviceArea() {
        return serviceArea;
    }

    public boolean isActive() {
        return active;
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
}
