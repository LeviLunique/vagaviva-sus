package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.engagement.application.port.in.PurgeNotificationBodiesUseCase;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import java.time.Clock;
import java.time.Period;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimização (LGPD): o texto enviado ao paciente (com nome, data e link) só fica guardado pelo prazo de retenção. */
@Service
class NotificationRetentionService implements PurgeNotificationBodiesUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionService.class);

    private final NotificationRepository notifications;
    private final Period retention;
    private final Clock clock;

    NotificationRetentionService(NotificationRepository notifications,
            @Value("${vagaviva.retention.notification-body}") Period retention, Clock clock) {
        this.notifications = notifications;
        this.retention = retention;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int purge() {
        int purged = notifications.purgeBodiesOlderThan(ZonedDateTime.now(clock).minus(retention).toInstant());
        if (purged > 0) {
            log.info("Retenção das mensagens: texto de {} mensagem(ns) com mais de {} removido.", purged, retention);
        }
        return purged;
    }
}
