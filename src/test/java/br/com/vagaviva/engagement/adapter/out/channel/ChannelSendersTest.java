package br.com.vagaviva.engagement.adapter.out.channel;

import static br.com.vagaviva.engagement.fixtures.EngagementFixture.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.engagement.application.service.EngagementProperties.ChannelMode;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.socialmessaging.SocialMessagingClient;
import software.amazon.awssdk.services.socialmessaging.model.SendWhatsAppMessageRequest;
import software.amazon.awssdk.services.socialmessaging.model.SendWhatsAppMessageResponse;
import tools.jackson.databind.json.JsonMapper;

class ChannelSendersTest {

    @Test
    @DisplayName("SMS: publica no SNS como transacional, para o número em E.164")
    void smsUsesTransactionalSns() {
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("sns-1").build());

        String id = new SmsNotificationSender(sns).send("+5511999990001", "texto");

        var captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        assertThat(id).isEqualTo("sns-1");
        assertThat(captor.getValue().phoneNumber()).isEqualTo("+5511999990001");
        assertThat(captor.getValue().messageAttributes().get(SmsNotificationSender.SMS_TYPE).stringValue())
                .isEqualTo("Transactional");
        assertThat(new SmsNotificationSender(sns).channel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    @DisplayName("WhatsApp: template utilitário aprovado, com o texto como parâmetro, pelo número de origem configurado")
    void whatsAppUsesUtilityTemplate() {
        SocialMessagingClient client = mock(SocialMessagingClient.class);
        when(client.sendWhatsAppMessage(any(SendWhatsAppMessageRequest.class)))
                .thenReturn(SendWhatsAppMessageResponse.builder().messageId("wa-1").build());
        var sender = new WhatsAppNotificationSender(client, properties(ChannelMode.LIVE, false, true),
                JsonMapper.builder().build());

        String id = sender.send("+5511999990001", "Olá, Maria");

        var captor = ArgumentCaptor.forClass(SendWhatsAppMessageRequest.class);
        verify(client).sendWhatsAppMessage(captor.capture());
        String payload = captor.getValue().message().asString(StandardCharsets.UTF_8);
        assertThat(id).isEqualTo("wa-1");
        assertThat(captor.getValue().originationPhoneNumberId()).isEqualTo("phone-number-id-1");
        assertThat(payload).contains("\"name\":\"vagaviva_aviso\"").contains("\"to\":\"+5511999990001\"")
                .contains("Olá, Maria").contains("\"type\":\"template\"");
        assertThat(sender.channel()).isEqualTo(NotificationChannel.WHATSAPP);
    }

    @Test
    @DisplayName("sandbox só registra: devolve um id próprio e não chama nenhum provedor")
    void sandboxOnlyRecords() {
        var sandbox = new SandboxNotificationSender();

        assertThat(sandbox.send("+5511999990001", "texto")).startsWith("sandbox-");
        assertThat(sandbox.channel()).isEqualTo(NotificationChannel.SANDBOX);
    }
}
