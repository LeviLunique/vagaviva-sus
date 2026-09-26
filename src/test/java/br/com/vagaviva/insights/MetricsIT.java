package br.com.vagaviva.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.vagaviva.engagement.ActiveConfirmationTestData;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** RF-36: um fluxo real (publicar ⇒ alocar ⇒ notificar) incrementa as métricas de negócio. */
@IntegrationTest
@Import({SchedulingTestData.class, ActiveConfirmationTestData.class})
class MetricsIT {

    @Autowired ActiveConfirmationTestData data;
    @Autowired MeterRegistry meters;

    private double counter(String name, String... tags) {
        var found = meters.find(name).tags(tags).counters();
        return found.stream().mapToDouble(c -> c.count()).sum();
    }

    private long allocations() {
        Timer timer = meters.find("vagaviva.allocation.duration").timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    @DisplayName("agendamento, alocação cronometrada e mensagem enviada aparecem nas métricas")
    void businessCountersMove() {
        double scheduled = counter("vagaviva.appointments.scheduled", "origin", "REGULAR");
        double sent = counter("vagaviva.notifications", "status", "SENT", "type", "APPOINTMENT_SCHEDULED");
        long runs = allocations();

        Referral referral = data.waitingReferral();
        data.publishSlot(referral, 9);
        UUID appointment = data.awaitPendingAppointment(referral.id());
        data.awaitDeliveredToken(appointment);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(counter("vagaviva.appointments.scheduled", "origin", "REGULAR")).isGreaterThan(scheduled);
            assertThat(counter("vagaviva.notifications", "status", "SENT", "type", "APPOINTMENT_SCHEDULED"))
                    .isGreaterThan(sent);
        });
        assertThat(allocations()).isGreaterThan(runs);
        assertThat(meters.find("vagaviva.notifications").tag("channel", "SANDBOX").counters()).isNotEmpty();
    }
}
