package br.com.vagaviva.insights.adapter.in.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.insights.application.port.in.ProjectEventsUseCase;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotReleased;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InsightsEventListenerTest {

    @Test
    @DisplayName("cada evento de agenda e encaixe vai para a projeção dos indicadores")
    void shouldForwardEveryEvent() {
        ProjectEventsUseCase projection = mock(ProjectEventsUseCase.class);
        var listener = new InsightsEventListener(projection);
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        UUID id = UUID.randomUUID();
        var scheduled = new AppointmentScheduled(id, id, id, id, id, id, now, AppointmentOrigin.REGULAR, now, now);
        var confirmed = new AppointmentConfirmed(id, id, id, id, now);
        var attended = new AppointmentAttended(id, id, id, id, now);
        var missed = new AppointmentMissed(id, id, id, id, now);
        var cancelled = new AppointmentCancelled(id, id, id, id, now, AppointmentCancelled.Reason.UNIT);
        var expired = new AppointmentExpired(id, id, id, id, now);
        var released = new SlotReleased(id, id, id, SlotReleased.Reason.WITHDRAWN, SlotStatus.AVAILABLE, now, 1, now);
        var accepted = new SlotOfferAccepted(id, id, id, id, id, 1, now);
        var lost = new SlotLost(id, id, id, now);

        listener.on(scheduled);
        listener.on(confirmed);
        listener.on(attended);
        listener.on(missed);
        listener.on(cancelled);
        listener.on(expired);
        listener.on(released);
        listener.on(accepted);
        listener.on(lost);

        verify(projection).project(scheduled);
        verify(projection).project(confirmed);
        verify(projection).project(attended);
        verify(projection).project(missed);
        verify(projection).project(cancelled);
        verify(projection).project(expired);
        verify(projection).project(released);
        verify(projection).project(accepted);
        verify(projection).project(lost);
    }
}
