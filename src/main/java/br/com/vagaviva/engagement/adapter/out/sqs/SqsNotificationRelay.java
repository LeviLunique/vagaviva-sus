package br.com.vagaviva.engagement.adapter.out.sqs;

import br.com.vagaviva.engagement.application.service.EngagementProperties;
import br.com.vagaviva.engagement.events.NotificationDispatchRequested;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Outbox → SQS (RF-24 CA1). O pedido de envio foi gravado no registro de eventos junto com a
 * notificação; depois do commit ele é publicado na fila. Se o SQS falhar, a publicação fica pendente
 * e é reenviada (na reinicialização ou por reprocessamento) — nenhuma mensagem se perde.
 */
@Component
class SqsNotificationRelay {

    private final SqsTemplate sqs;
    private final String queueName;

    SqsNotificationRelay(SqsTemplate sqs, EngagementProperties properties) {
        this.sqs = sqs;
        this.queueName = properties.queueName();
    }

    @ApplicationModuleListener
    void on(NotificationDispatchRequested request) {
        sqs.send(queueName, request);
    }
}
