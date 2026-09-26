package br.com.vagaviva.engagement.adapter.out.channel;

import br.com.vagaviva.engagement.application.port.out.NotificationSender;
import br.com.vagaviva.engagement.application.service.EngagementProperties;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.socialmessaging.SocialMessagingClient;
import software.amazon.awssdk.services.socialmessaging.model.SendWhatsAppMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * WhatsApp pelo AWS End User Messaging Social. Mensagem iniciada pela empresa exige template
 * aprovado pela Meta: usa um template utilitário de um parâmetro ({@code {{1}}} = texto da mensagem).
 * Desabilitado por padrão ({@code whatsapp.enabled}).
 */
@Component
@ConditionalOnBooleanProperty("vagaviva.engagement.whatsapp.enabled")
class WhatsAppNotificationSender implements NotificationSender {

    static final String META_API_VERSION = "v20.0";

    private final SocialMessagingClient client;
    private final EngagementProperties.WhatsApp settings;
    private final JsonMapper json;

    WhatsAppNotificationSender(SocialMessagingClient client, EngagementProperties properties, JsonMapper json) {
        this.client = client;
        this.settings = properties.whatsapp();
        this.json = json;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public String send(String phoneE164, String body) {
        Map<String, Object> message = Map.of(
                "messaging_product", "whatsapp",
                "to", phoneE164,
                "type", "template",
                "template", Map.of(
                        "name", settings.templateName(),
                        "language", Map.of("code", "pt_BR"),
                        "components", List.of(Map.of("type", "body",
                                "parameters", List.of(Map.of("type", "text", "text", body))))));
        return client.sendWhatsAppMessage(SendWhatsAppMessageRequest.builder()
                .originationPhoneNumberId(settings.originationPhoneNumberId())
                .metaApiVersion(META_API_VERSION)
                .message(SdkBytes.fromUtf8String(json.writeValueAsString(message)))
                .build()).messageId();
    }
}
