package br.com.vagaviva.engagement.application.port.in;

import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;

/** RF-24: cria as mensagens dos marcos do paciente — idempotente por marco (RN-24). */
public interface NotifyPatientUseCases {

    void referralQueued(ReferralQueued event);

    void appointmentScheduled(AppointmentScheduled event);

    void appointmentCancelled(AppointmentCancelled event);

    /** Lembretes de confirmação e de véspera (RN-11). @return quantos foram criados */
    int sendDueReminders();
}
