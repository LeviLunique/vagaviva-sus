package br.com.vagaviva.insights.application.port.in;

import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotReleased;

/** Projeções dos eventos nos fatos dos indicadores — idempotentes (reprocessar não muda nada). */
public interface ProjectEventsUseCase {

    void project(AppointmentScheduled event);

    void project(AppointmentConfirmed event);

    void project(AppointmentAttended event);

    void project(AppointmentMissed event);

    void project(AppointmentCancelled event);

    void project(AppointmentExpired event);

    void project(SlotReleased event);

    void project(SlotOfferAccepted event);

    void project(SlotLost event);
}
