package br.com.vagaviva.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);

    private static Notification pending() {
        return Notification.create(UUID.randomUUID(), UUID.randomUUID(), null, null, NotificationType.APPOINTMENT_SCHEDULED,
                NotificationChannel.SMS, "a".repeat(64), "texto", CLOCK);
    }

    @Test
    @DisplayName("envio com sucesso conta a tentativa, guarda o id do provedor e limpa o erro anterior")
    void shouldMarkSent() {
        Notification notification = pending();
        notification.recordFailure("timeout");

        notification.markSent("msg-1", CLOCK);

        assertThat(notification.isSent()).isTrue();
        assertThat(notification.attempts()).isEqualTo(2);
        assertThat(notification.providerMessageId()).isEqualTo("msg-1");
        assertThat(notification.lastError()).isNull();
        assertThat(notification.sentAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("RN-24: na 5ª falha desiste (FAILED); erro longo é truncado; erro nulo vira texto padrão")
    void shouldFailAfterMaxAttempts() {
        Notification notification = pending();
        notification.recordFailure(null);
        assertThat(notification.lastError()).isEqualTo("erro desconhecido");
        for (int i = 0; i < 3; i++) {
            notification.recordFailure("x".repeat(500));
        }
        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);

        notification.recordFailure("provedor fora");

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.attempts()).isEqualTo(Notification.MAX_ATTEMPTS);
        Notification longError = pending();
        longError.recordFailure("x".repeat(500));
        assertThat(longError.lastError()).hasSize(300);
    }
}
