package br.com.vagaviva.engagement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationRetentionServiceTest {

    @Test
    @DisplayName("SPEC §10: remove o texto das mensagens com mais de 90 dias")
    void shouldPurgeBodiesOlderThanRetention() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-12-31T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        when(notifications.purgeBodiesOlderThan(Instant.parse("2026-10-02T12:00:00Z"))).thenReturn(7, 0);
        var service = new NotificationRetentionService(notifications, Period.ofDays(90), clock);

        assertThat(service.purge()).isEqualTo(7);
        assertThat(service.purge()).isZero();
    }
}
