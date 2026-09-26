package br.com.vagaviva.engagement.application.port.out;

import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases.NotificationFilter;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    /** RN-24: já existe notificação deste tipo para o agendamento? */
    boolean existsForAppointment(UUID appointmentId, NotificationType type);

    boolean existsForReferral(UUID referralId, NotificationType type);

    /** Mais recentes primeiro. */
    Page<Notification> search(NotificationFilter filter, Pageable pageable);

    /** Caixa de entrada do canal SANDBOX de um paciente (demonstração), mais recentes primeiro. */
    List<Notification> findSandbox(UUID patientId, int limit);
}
