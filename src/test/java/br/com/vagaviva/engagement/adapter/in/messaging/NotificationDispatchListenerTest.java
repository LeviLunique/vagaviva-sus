package br.com.vagaviva.engagement.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.engagement.application.port.in.DispatchNotificationUseCase;
import br.com.vagaviva.engagement.events.NotificationDispatchRequested;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDispatchListenerTest {

    @Test
    @DisplayName("consumidor da fila configurada entrega a notificação pedida")
    void shouldDispatchFromQueue() throws Exception {
        DispatchNotificationUseCase dispatch = mock(DispatchNotificationUseCase.class);
        UUID id = UUID.randomUUID();

        new NotificationDispatchListener(dispatch).on(new NotificationDispatchRequested(id));

        verify(dispatch).dispatch(id);
        var on = NotificationDispatchListener.class.getDeclaredMethod("on", NotificationDispatchRequested.class);
        assertThat(on.getAnnotation(SqsListener.class).value()).containsExactly("${vagaviva.engagement.queue-name}");
    }
}
