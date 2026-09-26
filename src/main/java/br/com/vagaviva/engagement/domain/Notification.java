package br.com.vagaviva.engagement.domain;

import br.com.vagaviva.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Mensagem ao paciente e o registro da sua entrega (RF-24, RF-28). */
public final class Notification {

    /** Mesmo limite de recebimentos da fila antes da DLQ (RN-24). */
    public static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ERROR_LENGTH = 300;

    private final UUID id;
    private final UUID patientId;
    private final @Nullable UUID appointmentId;
    private final @Nullable UUID offerId;
    private final @Nullable UUID referralId;
    private final NotificationType type;
    private final NotificationChannel channel;
    private NotificationStatus status;
    private final String destinationHash;
    private final String body;
    private int attempts;
    private @Nullable String providerMessageId;
    private @Nullable String lastError;
    private final Instant createdAt;
    private @Nullable Instant sentAt;

    private Notification(UUID id, UUID patientId, @Nullable UUID appointmentId, @Nullable UUID offerId,
            @Nullable UUID referralId, NotificationType type, NotificationChannel channel, NotificationStatus status,
            String destinationHash, String body, int attempts, @Nullable String providerMessageId,
            @Nullable String lastError, Instant createdAt, @Nullable Instant sentAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.appointmentId = appointmentId;
        this.offerId = offerId;
        this.referralId = referralId;
        this.type = Objects.requireNonNull(type, "type");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.status = Objects.requireNonNull(status, "status");
        this.destinationHash = Objects.requireNonNull(destinationHash, "destinationHash");
        this.body = Objects.requireNonNull(body, "body");
        this.attempts = attempts;
        this.providerMessageId = providerMessageId;
        this.lastError = lastError;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.sentAt = sentAt;
    }

    public static Notification create(UUID patientId, @Nullable UUID appointmentId, @Nullable UUID offerId,
            @Nullable UUID referralId, NotificationType type, NotificationChannel channel, String destinationHash,
            String body, Clock clock) {
        return new Notification(Ids.newId(), patientId, appointmentId, offerId, referralId, type, channel,
                NotificationStatus.PENDING, destinationHash, body, 0, null, null, clock.instant(), null);
    }

    public static Notification restore(UUID id, UUID patientId, @Nullable UUID appointmentId, @Nullable UUID offerId,
            @Nullable UUID referralId, NotificationType type, NotificationChannel channel, NotificationStatus status,
            String destinationHash, String body, int attempts, @Nullable String providerMessageId,
            @Nullable String lastError, Instant createdAt, @Nullable Instant sentAt) {
        return new Notification(id, patientId, appointmentId, offerId, referralId, type, channel, status,
                destinationHash, body, attempts, providerMessageId, lastError, createdAt, sentAt);
    }

    /** RN-24: enviada com sucesso uma única vez — reprocessamentos posteriores são ignorados. */
    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }

    public void markSent(@Nullable String providerId, Clock clock) {
        attempts++;
        status = NotificationStatus.SENT;
        providerMessageId = providerId;
        lastError = null;
        sentAt = clock.instant();
    }

    /** Falha do provedor: conta a tentativa; na última, desiste (a mensagem segue para a DLQ). */
    public void recordFailure(String error) {
        attempts++;
        lastError = error == null ? "erro desconhecido"
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        if (attempts >= MAX_ATTEMPTS) {
            status = NotificationStatus.FAILED;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public @Nullable UUID appointmentId() {
        return appointmentId;
    }

    public @Nullable UUID offerId() {
        return offerId;
    }

    public @Nullable UUID referralId() {
        return referralId;
    }

    public NotificationType type() {
        return type;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public NotificationStatus status() {
        return status;
    }

    public String destinationHash() {
        return destinationHash;
    }

    public String body() {
        return body;
    }

    public int attempts() {
        return attempts;
    }

    public @Nullable String providerMessageId() {
        return providerMessageId;
    }

    public @Nullable String lastError() {
        return lastError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable Instant sentAt() {
        return sentAt;
    }
}
