package br.com.vagaviva.engagement.application.port.in;

/** Retenção do texto das mensagens (SPEC §10: 90 dias) — o registro de envio (status, canal) permanece. */
public interface PurgeNotificationBodiesUseCase {

    /** @return quantas mensagens tiveram o texto removido */
    int purge();
}
