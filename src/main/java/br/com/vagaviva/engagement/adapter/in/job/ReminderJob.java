package br.com.vagaviva.engagement.adapter.in.job;

import br.com.vagaviva.engagement.application.port.in.NotifyPatientUseCases;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-11: lembretes de confirmação e de véspera, periodicamente (uma instância por vez). */
@Component
class ReminderJob {

    private final NotifyPatientUseCases notify;

    ReminderJob(NotifyPatientUseCases notify) {
        this.notify = notify;
    }

    @Scheduled(fixedDelayString = "${vagaviva.engagement.reminder-interval}",
            initialDelayString = "${vagaviva.engagement.reminder-initial-delay:PT2M}")
    @SchedulerLock(name = "reminders", lockAtMostFor = "PT8M")
    void run() {
        notify.sendDueReminders();
    }
}
