package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class NotificationQueryService implements NotificationQueryUseCases {

    private static final int MAX_INBOX = 50;

    private final NotificationRepository notifications;
    private final SchedulingApi scheduling;

    NotificationQueryService(NotificationRepository notifications, SchedulingApi scheduling) {
        this.notifications = notifications;
        this.scheduling = scheduling;
    }

    @Override
    public Page<Notification> list(NotificationFilter filter, Pageable pageable, CurrentUser actor) {
        if (actor.role() == Role.SCHEDULER) {
            boolean ownUnit = filter.appointmentId() != null && scheduling.findAppointmentView(filter.appointmentId())
                    .map(AppointmentView::unitId).filter(unit -> unit.equals(actor.unitId())).isPresent();
            if (!ownUnit) {
                throw EngagementErrors.outOfUnit();
            }
        }
        return notifications.search(filter, pageable);
    }

    @Override
    public List<Notification> sandboxInbox(UUID patientId, int limit) {
        return notifications.findSandbox(patientId, Math.min(Math.max(limit, 1), MAX_INBOX));
    }
}
