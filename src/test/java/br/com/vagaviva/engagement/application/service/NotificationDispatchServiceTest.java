package br.com.vagaviva.engagement.application.service;

import static br.com.vagaviva.engagement.fixtures.EngagementFixture.CLOCK;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.patient;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.application.port.out.NotificationSender;
import br.com.vagaviva.engagement.application.service.EngagementProperties.ChannelMode;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationStatus;
import br.com.vagaviva.engagement.domain.NotificationType;
import br.com.vagaviva.patient.PatientApi;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();

    @Mock NotificationRepository notifications;
    @Mock PatientApi patients;

    private final NotificationSender sms = mock(NotificationSender.class);
    private final NotificationSender sandbox = mock(NotificationSender.class);
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        when(sms.channel()).thenReturn(NotificationChannel.SMS);
        when(sandbox.channel()).thenReturn(NotificationChannel.SANDBOX);
        var retries = RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        service = new NotificationDispatchService(notifications, patients, List.of(sms, sandbox),
                new ResilientDelivery(retries, CircuitBreakerRegistry.ofDefaults()), CLOCK);
        lenient().when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(patients.findSummary(PATIENT)).thenReturn(Optional.of(patient(PATIENT, false, true)));
    }

    private Notification stored(NotificationChannel channel) {
        Notification notification = Notification.create(PATIENT, UUID.randomUUID(), null, null,
                NotificationType.APPOINTMENT_SCHEDULED, channel, "a".repeat(64), "texto", CLOCK);
        when(notifications.findById(notification.id())).thenReturn(Optional.of(notification));
        return notification;
    }

    @Test
    @DisplayName("envia pelo canal gravado na notificação, com o telefone buscado na hora, e marca SENT")
    void shouldSendThroughStoredChannel() {
        Notification notification = stored(NotificationChannel.SMS);
        when(sms.send("+5511999990001", "texto")).thenReturn("sns-1");

        service.dispatch(notification.id());

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.providerMessageId()).isEqualTo("sns-1");
        verify(sandbox, never()).send(any(), any());
    }

    @Test
    @DisplayName("RN-24: notificação já enviada (reentrega da fila) é ignorada; inexistente também")
    void shouldIgnoreAlreadySent() {
        Notification notification = stored(NotificationChannel.SMS);
        notification.markSent("sns-1", CLOCK);

        service.dispatch(notification.id());
        service.dispatch(UUID.randomUUID());

        verify(sms, never()).send(any(), any());
        verify(notifications, never()).save(any());
    }

    @Test
    @DisplayName("falha transitória: o Resilience4j repete e a mensagem sai na mesma entrega")
    void shouldRetryTransientFailures() {
        Notification notification = stored(NotificationChannel.SMS);
        when(sms.send(any(), any())).thenThrow(new IllegalStateException("timeout")).thenReturn("sns-2");

        service.dispatch(notification.id());

        verify(sms, times(2)).send(any(), any());
        assertThat(notification.isSent()).isTrue();
    }

    @Test
    @DisplayName("falha persistente: conta a tentativa, guarda o erro e relança (a fila reentrega até a DLQ)")
    void shouldRecordFailureAndRethrow() {
        Notification notification = stored(NotificationChannel.SMS);
        when(sms.send(any(), any())).thenThrow(new IllegalStateException("provedor indisponível"));

        assertThatThrownBy(() -> service.dispatch(notification.id())).isInstanceOf(RuntimeException.class);

        assertThat(notification.attempts()).isEqualTo(1);
        assertThat(notification.lastError()).contains("provedor indisponível");
        verify(sms, times(3)).send(any(), any());
    }

    @Test
    @DisplayName("canal desabilitado (sem sender) ⇒ falha registrada; notificação FAILED não é reenviada")
    void shouldFailWhenChannelDisabled() {
        Notification whatsapp = stored(NotificationChannel.WHATSAPP);
        assertThatThrownBy(() -> service.dispatch(whatsapp.id())).isInstanceOf(RuntimeException.class);
        assertThat(whatsapp.lastError()).contains("WHATSAPP desabilitado");

        Notification failed = stored(NotificationChannel.SMS);
        for (int i = 0; i < Notification.MAX_ATTEMPTS; i++) {
            failed.recordFailure("erro");
        }
        service.dispatch(failed.id());
        verify(sms, never()).send(any(), any());
    }

    @Test
    @DisplayName("canal efetivo: SANDBOX no modo sandbox; em LIVE, WhatsApp com aceite, senão SMS, senão sandbox")
    void shouldSelectEffectiveChannel() {
        UUID id = UUID.randomUUID();
        assertThat(new ChannelSelector(properties(ChannelMode.SANDBOX, true, true)).channelFor(patient(id, true, true)))
                .isEqualTo(NotificationChannel.SANDBOX);
        assertThat(new ChannelSelector(properties(ChannelMode.LIVE, true, true)).channelFor(patient(id, true, true)))
                .isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(new ChannelSelector(properties(ChannelMode.LIVE, true, true)).channelFor(patient(id, false, true)))
                .isEqualTo(NotificationChannel.SMS);
        assertThat(new ChannelSelector(properties(ChannelMode.LIVE, false, false)).channelFor(patient(id, true, true)))
                .isEqualTo(NotificationChannel.SANDBOX);
    }
}
