package br.com.vagaviva.insights.adapter.in.event;

import br.com.vagaviva.insights.application.port.in.ProjectEventsUseCase;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotReleased;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Eventos da agenda e do encaixe ⇒ fatos dos indicadores (após o commit, com reentrega garantida). */
@Component
class InsightsEventListener {

    private final ProjectEventsUseCase projection;

    InsightsEventListener(ProjectEventsUseCase projection) {
        this.projection = projection;
    }

    @ApplicationModuleListener
    void on(AppointmentScheduled event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(AppointmentConfirmed event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(AppointmentAttended event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(AppointmentMissed event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(AppointmentCancelled event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(AppointmentExpired event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(SlotReleased event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(SlotOfferAccepted event) {
        projection.project(event);
    }

    @ApplicationModuleListener
    void on(SlotLost event) {
        projection.project(event);
    }
}
