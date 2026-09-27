package br.com.vagaviva.engagement.application.port.in;

import java.util.UUID;

/** Envia uma notificação pelo canal dela (chamado a partir da fila SQS). */
public interface DispatchNotificationUseCase {

    /**
     * Idempotente: notificação já enviada é ignorada (RN-24). Falha do provedor ⇒ exceção, e a fila
     * reentrega (após 5 recebimentos a mensagem vai para a DLQ).
     */
    void dispatch(UUID notificationId);
}
