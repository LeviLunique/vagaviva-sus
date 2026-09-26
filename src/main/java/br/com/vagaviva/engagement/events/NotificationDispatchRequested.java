package br.com.vagaviva.engagement.events;

import java.util.UUID;

/**
 * Uma notificação foi criada e deve ser enviada. Gravado no outbox na mesma transação da
 * notificação e repassado à fila SQS depois do commit; também é o corpo da mensagem na fila.
 */
public record NotificationDispatchRequested(UUID notificationId) {
}
