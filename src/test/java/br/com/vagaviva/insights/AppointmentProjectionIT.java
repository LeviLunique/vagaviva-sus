package br.com.vagaviva.insights;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.insights.application.port.in.ProjectEventsUseCase;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotReleased;
import br.com.vagaviva.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reprocessar o mesmo evento (reentrega do outbox) não duplica fatos nem métricas. */
@IntegrationTest
class AppointmentProjectionIT {

    @Autowired ProjectEventsUseCase projection;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meters;
    @Autowired Clock clock;

    private double counter(String name, String... tags) {
        var found = meters.find(name).tags(tags).counter();
        return found == null ? 0 : found.count();
    }

    @Test
    @DisplayName("agendamento, atendimento e liberação processados 2× ⇒ uma linha de cada e métrica uma vez")
    void reprocessingIsIdempotent() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID unit = UUID.randomUUID();
        UUID specialty = UUID.randomUUID();
        var scheduled = new AppointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), unit, specialty, now.plus(Duration.ofDays(6)), AppointmentOrigin.REGULAR,
                now.plus(Duration.ofDays(3)), now.minus(Duration.ofDays(30)));
        var attended = new AppointmentAttended(scheduled.appointmentId(), scheduled.referralId(), unit, specialty,
                scheduled.startAt());
        var released = new SlotReleased(UUID.randomUUID(), unit, specialty, SlotReleased.Reason.WITHDRAWN,
                SlotStatus.AVAILABLE, now.plus(Duration.ofDays(8)), 1, now);
        double scheduledBefore = counter("vagaviva.appointments.scheduled", "origin", "REGULAR");
        double attendedBefore = counter("vagaviva.appointments.outcome", "status", "ATTENDED");
        double releasedBefore = counter("vagaviva.slots.released", "reason", "WITHDRAWN");

        for (int i = 0; i < 2; i++) {
            projection.project(scheduled);
            projection.project(attended);
            projection.project(released);
        }

        Map<String, Object> fact = jdbc.queryForMap("select final_status, waiting_days, confirmed from appointment_fact "
                + "where appointment_id = ?", scheduled.appointmentId());
        assertThat(fact).containsEntry("final_status", "ATTENDED").containsEntry("confirmed", false);
        assertThat(fact.get("waiting_days")).hasToString("36.0");
        assertThat(jdbc.queryForObject("select count(*) from slot_release_fact where slot_id = ?", Long.class,
                released.slotId())).isEqualTo(1);
        assertThat(counter("vagaviva.appointments.scheduled", "origin", "REGULAR") - scheduledBefore).isEqualTo(1.0);
        assertThat(counter("vagaviva.appointments.outcome", "status", "ATTENDED") - attendedBefore).isEqualTo(1.0);
        assertThat(counter("vagaviva.slots.released", "reason", "WITHDRAWN") - releasedBefore).isEqualTo(1.0);
    }
}
