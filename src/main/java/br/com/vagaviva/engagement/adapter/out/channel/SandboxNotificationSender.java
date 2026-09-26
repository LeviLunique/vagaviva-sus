package br.com.vagaviva.engagement.adapter.out.channel;

import br.com.vagaviva.engagement.application.port.out.NotificationSender;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Canal de demonstração: não envia nada para fora — a mensagem (com o link) fica consultável na
 * caixa de entrada sandbox (RF-29). Nunca registra telefone nem corpo em log.
 */
@Component
class SandboxNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SANDBOX;
    }

    @Override
    public String send(String phoneE164, String body) {
        return "sandbox-" + UUID.randomUUID();
    }
}
