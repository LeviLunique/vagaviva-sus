package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.engagement.application.port.in.DispatchNotificationUseCase;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.application.port.out.NotificationSender;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationStatus;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Consome a fila: carrega a notificação, ignora se já enviada (RN-24), busca o telefone na hora
 * (o registro da notificação guarda só o hash) e envia pelo canal gravado nela.
 */
@Service
class NotificationDispatchService implements DispatchNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationRepository notifications;
    private final PatientApi patients;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final ResilientDelivery delivery;
    private final Clock clock;

    NotificationDispatchService(NotificationRepository notifications, PatientApi patients,
            List<NotificationSender> senders, ResilientDelivery delivery, Clock clock) {
        this.notifications = notifications;
        this.patients = patients;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.delivery = delivery;
        this.clock = clock;
    }

    @Override
    public void dispatch(UUID notificationId) {
        Optional<Notification> found = notifications.findById(notificationId);
        if (found.isEmpty()) {
            log.warn("Notificação {} não encontrada: nada a enviar.", notificationId);
            return;
        }
        Notification notification = found.get();
        if (notification.isSent() || notification.status() == NotificationStatus.FAILED) {
            return;
        }
        try {
            NotificationSender sender = senderFor(notification.channel());
            String phone = patients.findSummary(notification.patientId()).map(PatientSummary::phone)
                    .orElseThrow(() -> new IllegalStateException("paciente sem telefone"));
            String providerId = delivery.call(() -> sender.send(phone, notification.body()));
            notification.markSent(providerId, clock);
            notifications.save(notification);
        } catch (RuntimeException ex) {
            notification.recordFailure(ex.getMessage());
            notifications.save(notification);
            throw new NotificationDeliveryException(notificationId, ex);
        }
    }

    private NotificationSender senderFor(NotificationChannel channel) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            throw new IllegalStateException("canal " + channel + " desabilitado");
        }
        return sender;
    }

    /** Falha de entrega: a mensagem não é confirmada na fila e volta a ser entregue (até a DLQ). */
    static final class NotificationDeliveryException extends RuntimeException {
        NotificationDeliveryException(UUID notificationId, Throwable cause) {
            super("Falha ao enviar a notificação " + notificationId, cause);
        }
    }
}
