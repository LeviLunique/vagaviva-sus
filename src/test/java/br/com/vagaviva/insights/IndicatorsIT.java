package br.com.vagaviva.insights;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.insights.application.port.in.IndicatorsQuery;
import br.com.vagaviva.insights.application.port.in.IndicatorsQuery.IndicatorFilter;
import br.com.vagaviva.insights.application.port.in.ProjectEventsUseCase;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotReleased;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** RF-35: cenário conhecido ⇒ números exatos (fatos projetados + fila real). */
@IntegrationTest
@Import(SchedulingTestData.class)
class IndicatorsIT {

    @Autowired ProjectEventsUseCase projection;
    @Autowired IndicatorsQuery indicators;
    @Autowired SchedulingTestData data;
    @Autowired Clock clock;

    private final UUID unit = UUID.randomUUID();
    private final UUID specialty = UUID.randomUUID();

    private UUID appointment(Instant startAt, AppointmentOrigin origin, int waitedDays) {
        var event = new AppointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                unit, specialty, startAt, origin, startAt.minus(Duration.ofDays(3)),
                startAt.minus(Duration.ofDays(waitedDays)));
        projection.project(event);
        return event.appointmentId();
    }

    @Test
    @DisplayName("3 atendidos + 1 falta, 3 confirmados + 1 expirado, 1 cancelado; 4 liberações: 2 reaproveitadas, 1 perdida")
    void knownScenarioGivesExactNumbers() {
        Instant day = clock.instant().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(14));
        for (int waited : new int[] {10, 20, 30}) {
            UUID id = appointment(day, AppointmentOrigin.REGULAR, waited);
            projection.project(new AppointmentConfirmed(id, UUID.randomUUID(), unit, specialty, day));
            projection.project(new AppointmentAttended(id, UUID.randomUUID(), unit, specialty, day));
        }
        UUID missed = appointment(day, AppointmentOrigin.SHORT_NOTICE_OFFER, 5);
        projection.project(new AppointmentMissed(missed, UUID.randomUUID(), unit, specialty, day));
        UUID expired = appointment(day, AppointmentOrigin.REGULAR, 7);
        projection.project(new AppointmentExpired(expired, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), day));
        UUID cancelled = appointment(day, AppointmentOrigin.REGULAR, 7);
        projection.project(new AppointmentCancelled(cancelled, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                day, AppointmentCancelled.Reason.PATIENT));

        Instant now = clock.instant();
        UUID[] slots = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        for (UUID slot : slots) {
            projection.project(new SlotReleased(slot, unit, specialty, SlotReleased.Reason.PATIENT_CANCELLED,
                    SlotStatus.OPEN_FOR_OFFERS, day, 1, now.minus(Duration.ofHours(6))));
        }
        projection.project(new AppointmentScheduled(UUID.randomUUID(), slots[0], UUID.randomUUID(), UUID.randomUUID(),
                unit, specialty, day, AppointmentOrigin.REALLOCATED, day, null));
        projection.project(new SlotOfferAccepted(UUID.randomUUID(), slots[1], UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, day));
        projection.project(new SlotLost(slots[2], unit, specialty, day));
        data.waitingReferral(specialty, RiskClass.RED, now.minus(Duration.ofDays(3)));
        data.waitingReferral(specialty, RiskClass.RED, now.minus(Duration.ofDays(2)));
        data.waitingReferral(specialty, RiskClass.BLUE, now.minus(Duration.ofDays(1)));

        LocalDate today = LocalDate.now(clock);
        var result = indicators.indicators(new IndicatorFilter(today.minusDays(1), today.plusDays(5), specialty, unit));

        assertThat(result.appointments().scheduled()).isEqualTo(7);
        assertThat(result.appointments().attended()).isEqualTo(3);
        assertThat(result.appointments().noShows()).isEqualTo(1);
        assertThat(result.appointments().cancelled()).isEqualTo(1);
        assertThat(result.absenteeismRate()).isEqualByComparingTo("0.2500");
        assertThat(result.confirmationRate()).isEqualByComparingTo("0.7500");
        assertThat(result.avgWaitingDays()).isEqualByComparingTo("20.0");
        assertThat(result.slots().released()).isEqualTo(4);
        assertThat(result.slots().reallocated()).isEqualTo(2);
        assertThat(result.slots().lost()).isEqualTo(1);
        assertThat(result.reuseRate()).isEqualByComparingTo("0.5000");
        assertThat(result.medianReoccupationHours()).isEqualByComparingTo("6.0");
        assertThat(result.queueSizeByRisk()).containsEntry("RED", 2L).containsEntry("BLUE", 1L).containsEntry("GREEN", 0L);
        assertThat(result.estimatedMessagingCostBrl()).isNotNull();

        var other = indicators.indicators(new IndicatorFilter(today.minusDays(1), today.plusDays(5), specialty,
                UUID.randomUUID()));
        assertThat(other.appointments().scheduled()).isZero();
        assertThat(other.absenteeismRate()).isNull();

        var bySpecialty = indicators.bySpecialty(today.minusDays(1), today.plusDays(5)).stream()
                .filter(s -> s.specialtyId().equals(specialty)).findFirst().orElseThrow();
        assertThat(bySpecialty.indicators().appointments().scheduled()).isEqualTo(7);
        assertThat(bySpecialty.indicators().reuseRate()).isEqualByComparingTo("0.5000");
        assertThat(bySpecialty.indicators().estimatedMessagingCostBrl()).isNull();
    }
}
