package br.com.vagaviva.scheduling.domain;

import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.Ids;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Vaga de consulta ou exame publicada por uma unidade executante (RF-19). */
public final class Slot {

    public static final int MIN_DURATION_MINUTES = 5;
    public static final int MAX_DURATION_MINUTES = 240;

    private final UUID id;
    private final UUID unitId;
    private final UUID specialtyId;
    private final String professionalName;
    private final Instant startAt;
    private final int durationMinutes;
    private SlotStatus status;
    private int releaseCount;
    private final Instant createdAt;
    private Instant updatedAt;
    private final @Nullable Long version;

    private Slot(UUID id, UUID unitId, UUID specialtyId, String professionalName, Instant startAt, int durationMinutes,
            SlotStatus status, int releaseCount, Instant createdAt, Instant updatedAt, @Nullable Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.unitId = Objects.requireNonNull(unitId, "unitId");
        this.specialtyId = Objects.requireNonNull(specialtyId, "specialtyId");
        this.professionalName = Objects.requireNonNull(professionalName, "professionalName");
        this.startAt = Objects.requireNonNull(startAt, "startAt");
        this.durationMinutes = durationMinutes;
        this.status = Objects.requireNonNull(status, "status");
        this.releaseCount = releaseCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /**
     * RF-19 / RN-09: com ≥ 5 dias de antecedência a vaga entra na alocação regular ({@code AVAILABLE});
     * entre 2 h e 5 dias vai direto para o encaixe ({@code OPEN_FOR_OFFERS}); com menos de 2 h ou no
     * passado não é publicada.
     */
    public static Slot publish(UUID unitId, UUID specialtyId, String professionalName, Instant startAt,
            int durationMinutes, SlotLeadTimes leadTimes, Clock clock) {
        Instant now = clock.instant();
        if (!startAt.isAfter(now)) {
            throw new BusinessRuleException("SLOT_IN_PAST", "Não é possível publicar vaga no passado.");
        }
        if (startAt.isBefore(now.plus(leadTimes.shortNotice()))) {
            throw new BusinessRuleException("SLOT_TOO_SOON",
                    "Vagas com início em menos de %d horas não são ofertadas.".formatted(leadTimes.shortNotice().toHours()));
        }
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new BusinessRuleException("INVALID_SLOT_DURATION",
                    "A duração deve ficar entre %d e %d minutos.".formatted(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES));
        }
        if (professionalName == null || professionalName.isBlank()) {
            throw new BusinessRuleException("PROFESSIONAL_REQUIRED", "Informe o profissional da vaga.");
        }
        SlotStatus initial = startAt.isBefore(now.plus(leadTimes.regularAllocation()))
                ? SlotStatus.OPEN_FOR_OFFERS
                : SlotStatus.AVAILABLE;
        return new Slot(Ids.newId(), unitId, specialtyId, professionalName.strip(), startAt, durationMinutes, initial,
                0, now, now, null);
    }

    public static Slot restore(UUID id, UUID unitId, UUID specialtyId, String professionalName, Instant startAt,
            int durationMinutes, SlotStatus status, int releaseCount, Instant createdAt, Instant updatedAt,
            @Nullable Long version) {
        return new Slot(id, unitId, specialtyId, professionalName, startAt, durationMinutes, status, releaseCount,
                createdAt, updatedAt, version);
    }

    public Instant endAt() {
        return startAt.plus(Duration.ofMinutes(durationMinutes));
    }

    /** Mesmo profissional com horários sobrepostos (RF-19 CA1). */
    public boolean overlaps(Slot other) {
        return professionalName.equalsIgnoreCase(other.professionalName)
                && startAt.isBefore(other.endAt()) && other.startAt.isBefore(endAt());
    }

    public void allocate(Clock clock) {
        moveTo(SlotStatus.ALLOCATED, clock);
    }

    public void markUsed(Clock clock) {
        moveTo(SlotStatus.USED, clock);
    }

    public void markMissed(Clock clock) {
        moveTo(SlotStatus.MISSED, clock);
    }

    /**
     * RN-15: a vaga alocada é liberada para o destino decidido pela {@link ReleasePolicy} e conta
     * mais uma liberação (a próxima alocação regular sai como {@code REALLOCATED}).
     */
    public void release(SlotStatus destination, Clock clock) {
        if (status != SlotStatus.ALLOCATED) {
            throw new ConflictException("SLOT_INVALID_STATE", "Só uma vaga alocada pode ser liberada.");
        }
        moveTo(destination, clock);
        releaseCount++;
    }

    /** RF-23: a unidade cancela a vaga (não há volta — o horário deixa de existir). */
    public void cancel(Clock clock) {
        moveTo(SlotStatus.CANCELLED, clock);
    }

    /** A primeira alocação é {@code REGULAR}; depois de liberada, a próxima é {@code REALLOCATED}. */
    public boolean wasReleased() {
        return releaseCount > 0;
    }

    private void moveTo(SlotStatus target, Clock clock) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("SLOT_INVALID_STATE", "Operação não permitida para vaga em %s.".formatted(status));
        }
        status = target;
        updatedAt = clock.instant();
    }

    public UUID id() {
        return id;
    }

    public UUID unitId() {
        return unitId;
    }

    public UUID specialtyId() {
        return specialtyId;
    }

    public String professionalName() {
        return professionalName;
    }

    public Instant startAt() {
        return startAt;
    }

    public int durationMinutes() {
        return durationMinutes;
    }

    public SlotStatus status() {
        return status;
    }

    public int releaseCount() {
        return releaseCount;
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
