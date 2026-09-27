package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.engagement.EngagementApi;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EngagementApiImpl implements EngagementApi {

    private final NotificationRepository notifications;

    EngagementApiImpl(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> sentByChannel(Instant from, Instant to) {
        return notifications.countSentByChannel(from, to);
    }
}
