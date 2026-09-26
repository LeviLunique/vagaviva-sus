package br.com.vagaviva.engagement.adapter.in.event;

import br.com.vagaviva.engagement.application.port.in.NotifyPatientUseCases;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Marcos do paciente vindos da regulação e da agenda (após o commit, com reentrega garantida). */
@Component
class PatientMilestoneListener {

    private final NotifyPatientUseCases notify;

    PatientMilestoneListener(NotifyPatientUseCases notify) {
        this.notify = notify;
    }

    @ApplicationModuleListener
    void on(ReferralQueued event) {
        notify.referralQueued(event);
    }

    @ApplicationModuleListener
    void on(AppointmentScheduled event) {
        notify.appointmentScheduled(event);
    }

    @ApplicationModuleListener
    void on(AppointmentCancelled event) {
        notify.appointmentCancelled(event);
    }
}
