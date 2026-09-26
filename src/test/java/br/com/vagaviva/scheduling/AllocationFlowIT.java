package br.com.vagaviva.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import br.com.vagaviva.support.TestJwt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.Scenario;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Publicar agenda ⇒ o evento {@code SlotsPublished} (registro JDBC, após o commit) dispara a
 * alocação sozinho ⇒ {@code AppointmentScheduled} é publicado para o paciente da fila.
 */
@IntegrationTest
@Import(SchedulingTestData.class)
@EnableScenarios
class AllocationFlowIT {

    @Autowired MockMvcTester mvc;
    @Autowired SchedulingTestData data;
    @Autowired SpecialtyRepository specialties;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    @DisplayName("publicar vagas aloca automaticamente o paciente da fila e publica AppointmentScheduled")
    void publishingSlotsTriggersAllocation(Scenario scenario) {
        UUID specialty = specialties.save(Specialty.register("AF" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999),
                "Especialidade fluxo", SpecialtyType.CONSULTATION, false, clock)).id();
        UUID unit = data.specializedUnit(Set.of());
        Referral waiting = data.waitingReferral(specialty, RiskClass.RED, clock.instant().minus(Duration.ofDays(10)));
        Instant startAt = clock.instant().plus(Duration.ofDays(9)).truncatedTo(ChronoUnit.MINUTES);
        String body = """
                {"unitId":"%s","specialtyId":"%s","professionalName":"Dra. Fluxo","slots":[{"startAt":"%s","durationMinutes":30}]}"""
                .formatted(unit, specialty, startAt);

        scenario.stimulate(() -> assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), unit))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).hasStatus(HttpStatus.CREATED))
                .andWaitForEventOfType(AppointmentScheduled.class)
                .matching(event -> event.referralId().equals(waiting.id()))
                .toArriveAndVerify(event -> {
                    assertThat(event.patientId()).isEqualTo(waiting.patientId());
                    assertThat(event.startAt()).isEqualTo(startAt);
                    assertThat(event.confirmationDeadline()).isBefore(startAt.minus(Duration.ofDays(2)));
                    assertThat(event.queueEnteredAt()).isEqualTo(waiting.queueEnteredAt());
                });

        assertThat(jdbc.queryForObject("select status from referral where id = ?", String.class, waiting.id()))
                .isEqualTo("SCHEDULED");
    }
}
