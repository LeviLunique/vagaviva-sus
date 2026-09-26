package br.com.vagaviva.engagement.adapter.out.persistence;

import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationStatus;
import br.com.vagaviva.engagement.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "referral_id")
    private UUID referralId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "destination_hash", nullable = false, length = 64)
    private String destinationHash;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationEntity() {
    }

    static NotificationEntity from(Notification n) {
        var entity = new NotificationEntity();
        entity.id = n.id();
        entity.patientId = n.patientId();
        entity.appointmentId = n.appointmentId();
        entity.offerId = n.offerId();
        entity.referralId = n.referralId();
        entity.type = n.type();
        entity.channel = n.channel();
        entity.status = n.status();
        entity.destinationHash = n.destinationHash();
        entity.body = n.body();
        entity.attempts = (short) n.attempts();
        entity.providerMessageId = n.providerMessageId();
        entity.lastError = n.lastError();
        entity.createdAt = n.createdAt();
        entity.sentAt = n.sentAt();
        return entity;
    }

    Notification toDomain() {
        return Notification.restore(id, patientId, appointmentId, offerId, referralId, type, channel, status,
                destinationHash, body, attempts, providerMessageId, lastError, createdAt, sentAt);
    }
}
