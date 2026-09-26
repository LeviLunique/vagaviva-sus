package br.com.vagaviva.engagement.application.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code vagaviva.engagement}: modo dos canais, fila de envio e antecedência dos lembretes. */
@Validated
@ConfigurationProperties("vagaviva.engagement")
public record EngagementProperties(@NotNull ChannelMode channelMode, @Valid @NotNull Sms sms,
        @Valid @NotNull WhatsApp whatsapp, @NotBlank String queueName,
        @NotNull Duration confirmationReminderBeforeDeadline, @NotNull Duration attendanceReminderBeforeStart) {

    /** {@code SANDBOX}: toda mensagem só é registrada; {@code LIVE}: WhatsApp (com aceite) ou SMS. */
    public enum ChannelMode {
        SANDBOX,
        LIVE
    }

    public record Sms(boolean enabled) {
    }

    public record WhatsApp(boolean enabled, @Nullable String originationPhoneNumberId, @Nullable String templateName) {
    }
}
