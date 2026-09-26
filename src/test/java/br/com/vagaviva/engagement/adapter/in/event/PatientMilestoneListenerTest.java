package br.com.vagaviva.engagement.adapter.in.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.engagement.application.port.in.NotifyPatientUseCases;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientMilestoneListenerTest {

    @Test
    @DisplayName("cada marco do paciente vira um pedido de notificação")
    void shouldForwardMilestones() {
        NotifyPatientUseCases notify = mock(NotifyPatientUseCases.class);
        var listener = new PatientMilestoneListener(notify);
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        var queued = new ReferralQueued(UUID.randomUUID(), UUID.randomUUID(), "VV-2026-0000001");
        var scheduled = new AppointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), now, AppointmentOrigin.REGULAR, now, now);
        var cancelled = new AppointmentCancelled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), now, AppointmentCancelled.Reason.UNIT);

        listener.on(queued);
        listener.on(scheduled);
        listener.on(cancelled);

        verify(notify).referralQueued(queued);
        verify(notify).appointmentScheduled(scheduled);
        verify(notify).appointmentCancelled(cancelled);
    }
}
