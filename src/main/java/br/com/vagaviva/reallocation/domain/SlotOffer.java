package br.com.vagaviva.reallocation.domain;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Oferta de uma vaga de encaixe a um paciente, numa rodada. */
public final class SlotOffer {

    private final UUID id;
    private final UUID slotId;
    private final UUID referralId;
    private final UUID patientId;
    private final int round;
    private OfferStatus status;
    private final Instant expiresAt;
    private @Nullable Instant respondedAt;
    private final Instant createdAt;

    private SlotOffer(UUID id, UUID slotId, UUID referralId, UUID patientId, int round, OfferStatus status,
            Instant expiresAt, @Nullable Instant respondedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.slotId = Objects.requireNonNull(slotId, "slotId");
        this.referralId = Objects.requireNonNull(referralId, "referralId");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.round = round;
        this.status = Objects.requireNonNull(status, "status");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.respondedAt = respondedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static SlotOffer create(UUID slotId, UUID referralId, UUID patientId, int round, Instant expiresAt,
            Clock clock) {
        return new SlotOffer(Ids.newId(), slotId, referralId, patientId, round, OfferStatus.PENDING, expiresAt, null,
                clock.instant());
    }

    public static SlotOffer restore(UUID id, UUID slotId, UUID referralId, UUID patientId, int round,
            OfferStatus status, Instant expiresAt, @Nullable Instant respondedAt, Instant createdAt) {
        return new SlotOffer(id, slotId, referralId, patientId, round, status, expiresAt, respondedAt, createdAt);
    }

    /** RN-17: só oferta pendente e dentro do prazo pode ser aceita. */
    public void accept(Clock clock) {
        requireAnswerable(clock);
        status = OfferStatus.ACCEPTED;
        respondedAt = clock.instant();
    }

    /** @return {@code false} se já estava recusada (repetição idempotente) */
    public boolean decline(Clock clock) {
        if (status == OfferStatus.DECLINED) {
            return false;
        }
        requireAnswerable(clock);
        status = OfferStatus.DECLINED;
        respondedAt = clock.instant();
        return true;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void requireAnswerable(Clock clock) {
        if (status == OfferStatus.SUPERSEDED) {
            throw new ConflictException("SLOT_ALREADY_FILLED", "Vaga já preenchida por outro paciente.");
        }
        if (status != OfferStatus.PENDING) {
            throw new ConflictException("OFFER_NOT_PENDING", "Esta oferta já foi respondida ou encerrada.");
        }
        if (isExpired(clock.instant())) {
            throw new GoneException("OFFER_EXPIRED", "O prazo para responder a esta oferta terminou.");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID slotId() {
        return slotId;
    }

    public UUID referralId() {
        return referralId;
    }

    public UUID patientId() {
        return patientId;
    }

    public int round() {
        return round;
    }

    public OfferStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public @Nullable Instant respondedAt() {
        return respondedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
