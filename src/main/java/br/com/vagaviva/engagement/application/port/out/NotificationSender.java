package br.com.vagaviva.engagement.application.port.out;

import br.com.vagaviva.engagement.domain.NotificationChannel;
import org.jspecify.annotations.Nullable;

/** Strategy por canal (SANDBOX, SMS, WhatsApp). Falha do provedor ⇒ exceção (o chamador retenta). */
public interface NotificationSender {

    NotificationChannel channel();

    /** @return id da mensagem no provedor, se houver */
    @Nullable String send(String phoneE164, String body);
}
