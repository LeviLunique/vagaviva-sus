package br.com.vagaviva.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.ConfirmationPolicy;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import br.com.vagaviva.support.TestJwt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** RF-22/RF-23 com banco real: comparecimento, falta e cancelamento pela unidade refletem na fila. */
@IntegrationTest
@Import(SchedulingTestData.class)
class AttendanceIT {

    @Autowired MockMvcTester mvc;
    @Autowired SchedulingTestData data;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired ReferralRepository referrals;
    @Autowired ConfirmationPolicy confirmationPolicy;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    @DisplayName("check-in no dia ⇒ ATTENDED, vaga USED e encaminhamento COMPLETED")
    void checkInCompletesReferral() {
        UUID unit = data.specializedUnit(Set.of());
        Appointment appointment = scheduled(unit, clock.instant().plusSeconds(5));

        assertThat(mvc.post().uri("/api/v1/appointments/{id}/check-in", appointment.id()).with(scheduler(unit)))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ATTENDED");
        assertThat(status("slot", appointment.slotId())).isEqualTo("USED");
        assertThat(status("referral", appointment.referralId())).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("RN-14: falta após o início ⇒ NO_SHOW, vaga MISSED e encaminhamento de volta à regulação")
    void noShowSendsReferralToReview() {
        UUID unit = data.specializedUnit(Set.of());
        Appointment appointment = scheduled(unit, clock.instant().minus(Duration.ofHours(1)));

        assertThat(mvc.post().uri("/api/v1/appointments/{id}/no-show", appointment.id()).with(scheduler(unit)))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("NO_SHOW");
        assertThat(status("slot", appointment.slotId())).isEqualTo("MISSED");
        assertThat(status("referral", appointment.referralId())).isEqualTo("PENDING_REGULATION");
        assertThat(jdbc.queryForObject("select no_shows from referral where id = ?", Integer.class, appointment.referralId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("RF-23: unidade cancela a vaga ⇒ CANCELLED_BY_UNIT e o paciente volta à fila com a mesma data de entrada")
    void unitCancellationReturnsPatientToQueueInPlace() {
        UUID unit = data.specializedUnit(Set.of());
        Appointment appointment = scheduled(unit, clock.instant().plus(Duration.ofDays(10)));
        Instant entryBefore = referrals.findById(appointment.referralId()).orElseThrow().queueEnteredAt();

        assertThat(mvc.post().uri("/api/v1/slots/{id}/cancel", appointment.slotId()).with(scheduler(unit))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Profissional afastado\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");

        assertThat(status("appointment", appointment.id())).isEqualTo("CANCELLED_BY_UNIT");
        Referral back = referrals.findById(appointment.referralId()).orElseThrow();
        assertThat(back.status().name()).isEqualTo("WAITING");
        assertThat(back.queueEnteredAt()).isEqualTo(entryBefore);
    }

    @Test
    @DisplayName("check-in antes do dia ⇒ 422; SCHEDULER de outra unidade ⇒ 403")
    void shouldGuardAttendance() {
        UUID unit = data.specializedUnit(Set.of());
        Appointment future = scheduled(unit, clock.instant().plus(Duration.ofDays(10)));

        assertThat(mvc.post().uri("/api/v1/appointments/{id}/check-in", future.id()).with(scheduler(unit)))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("CHECK_IN_OUTSIDE_DAY");
        assertThat(mvc.post().uri("/api/v1/appointments/{id}/check-in", future.id())
                .with(scheduler(UUID.randomUUID()))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/appointments?unitId={u}", unit).with(TestJwt.as(Role.REGULATOR)))
                .hasStatusOk().bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    /** Agendamento criado direto pelas portas (a publicação exige ≥ 2 h de antecedência). */
    private Appointment scheduled(UUID unit, Instant startAt) {
        UUID specialty = UUID.randomUUID();
        Referral referral = data.waitingReferral(specialty, RiskClass.YELLOW, clock.instant().minus(Duration.ofDays(20)));
        Slot slot = slots.save(Slot.restore(UUID.randomUUID(), unit, specialty, "Dra. " + UUID.randomUUID(), startAt, 30,
                br.com.vagaviva.scheduling.SlotStatus.AVAILABLE, 0, clock.instant(), clock.instant(), null));
        slot.allocate(clock);
        Slot allocated = slots.save(slot);
        Referral loaded = referrals.findById(referral.id()).orElseThrow();
        loaded.markScheduled(clock);
        referrals.save(loaded);
        return appointments.save(Appointment.schedule(allocated, referral.id(), referral.patientId(), confirmationPolicy,
                clock));
    }

    private MockMvcTesterScheduler scheduler(UUID unit) {
        return new MockMvcTesterScheduler(unit);
    }

    private String status(String table, UUID id) {
        return jdbc.queryForObject("select status from " + table + " where id = ?", String.class, id);
    }

    /** Atalho para um SCHEDULER da unidade. */
    private record MockMvcTesterScheduler(UUID unit) implements org.springframework.test.web.servlet.request.RequestPostProcessor {
        @Override
        public org.springframework.mock.web.MockHttpServletRequest postProcessRequest(
                org.springframework.mock.web.MockHttpServletRequest request) {
            return TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), unit).postProcessRequest(request);
        }
    }
}
