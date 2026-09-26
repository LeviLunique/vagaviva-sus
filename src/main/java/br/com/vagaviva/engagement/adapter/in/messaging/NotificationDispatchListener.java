package br.com.vagaviva.engagement.adapter.in.messaging;

import br.com.vagaviva.engagement.application.port.in.DispatchNotificationUseCase;
import br.com.vagaviva.engagement.events.NotificationDispatchRequested;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila de notificações. Sucesso ⇒ a mensagem é apagada; exceção ⇒ volta à fila após
 * o visibility timeout e, depois de 5 recebimentos, vai para a DLQ (RN-24).
 */
@Component
class NotificationDispatchListener {

    private final DispatchNotificationUseCase dispatch;

    NotificationDispatchListener(DispatchNotificationUseCase dispatch) {
        this.dispatch = dispatch;
    }

    @SqsListener("${vagaviva.engagement.queue-name}")
    void on(NotificationDispatchRequested request) {
        dispatch.dispatch(request.notificationId());
    }
}
