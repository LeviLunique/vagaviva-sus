package br.com.vagaviva.engagement.application.port.in;

import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** RF-28 (log de entregas) e RF-29 (caixa de entrada sandbox da demonstração). */
public interface NotificationQueryUseCases {

    /** SCHEDULER precisa informar um agendamento da própria unidade (RN-03). */
    Page<Notification> list(NotificationFilter filter, Pageable pageable, CurrentUser actor);

    List<Notification> sandboxInbox(UUID patientId, int limit);

    record NotificationFilter(@Nullable UUID appointmentId, @Nullable UUID patientId) {
    }
}
