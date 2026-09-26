package br.com.vagaviva.scheduling.domain;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CLOCK;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CONFIRMATION;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.LEAD_TIMES;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.aPendingAppointment;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.anAvailableSlot;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppointmentTest {

    @Test
    @DisplayName("RF-20: agendamento pela fila aguarda confirmação até D-3 23h59, origem REGULAR na 1ª alocação")
    void shouldScheduleWithDeadline() {
        Appointment appointment = aPendingAppointment();

        assertThat(appointment.status()).isEqualTo(AppointmentStatus.PENDING_CONFIRMATION);
        assertThat(appointment.origin()).isEqualTo(AppointmentOrigin.REGULAR);
        // vaga em 05/10 08:00 (SP) ⇒ prazo 02/10 23:59 (SP) = 03/10 02:59Z
        assertThat(appointment.confirmationDeadline()).isEqualTo(Instant.parse("2026-10-03T02:59:00Z"));
        assertThat(appointment.id().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("vaga já liberada antes ⇒ origem REALLOCATED")
    void shouldMarkReallocatedWhenSlotWasReleased() {
        Slot released = Slot.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Dra. Ana",
                Instant.parse("2026-10-05T11:00:00Z"), 30, br.com.vagaviva.scheduling.SlotStatus.ALLOCATED, 1,
                CLOCK.instant(), CLOCK.instant(), 0L);

        assertThat(Appointment.schedule(released, UUID.randomUUID(), UUID.randomUUID(), CONFIRMATION, CLOCK).origin())
                .isEqualTo(AppointmentOrigin.REALLOCATED);
    }

    @Test
    @DisplayName("RN-19: check-in só no dia do atendimento (data civil de São Paulo)")
    void shouldCheckInOnlyOnTheDay() {
        Appointment early = aPendingAppointment();
        assertThatThrownBy(() -> early.checkIn(at(Instant.parse("2026-10-05T02:59:00Z"))))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CHECK_IN_OUTSIDE_DAY");

        // 05/10 00:01 em São Paulo já é o dia (03:01Z)
        Appointment onTheDay = aPendingAppointment();
        onTheDay.checkIn(at(Instant.parse("2026-10-05T03:01:00Z")));
        assertThat(onTheDay.status()).isEqualTo(AppointmentStatus.ATTENDED);
        assertThat(onTheDay.outcomeAt()).isEqualTo(Instant.parse("2026-10-05T03:01:00Z"));

        Appointment late = aPendingAppointment();
        assertThatThrownBy(() -> late.checkIn(at(Instant.parse("2026-10-06T03:01:00Z"))))
                .extracting("code").isEqualTo("CHECK_IN_OUTSIDE_DAY");
    }

    @Test
    @DisplayName("RN-19: falta só depois do início; registrada ⇒ NO_SHOW")
    void shouldMarkNoShowOnlyAfterStart() {
        Appointment appointment = aPendingAppointment();
        assertThatThrownBy(() -> appointment.markNoShow(at(Instant.parse("2026-10-05T10:59:00Z"))))
                .extracting("code").isEqualTo("NO_SHOW_BEFORE_START");

        appointment.markNoShow(at(Instant.parse("2026-10-05T11:00:00Z")));
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    @DisplayName("RF-23: a unidade cancela; depois do desfecho não há mais transição (409)")
    void shouldCancelByUnitAndBlockAfterOutcome() {
        Appointment cancelled = aPendingAppointment();
        cancelled.cancelByUnit(CLOCK);
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED_BY_UNIT);
        assertThat(cancelled.cancelledAt()).isEqualTo(CLOCK.instant());
        assertThat(cancelled.status().isOpen()).isFalse();

        assertThatThrownBy(() -> cancelled.markNoShow(at(Instant.parse("2026-10-06T00:00:00Z"))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("APPOINTMENT_INVALID_STATE");
        assertThat(aPendingAppointment().belongsToUnit(anAvailableSlot().unitId())).isTrue();
        assertThat(LEAD_TIMES.shortNotice()).isNotNull();
    }

    @Test
    @DisplayName("mapa de transições: estados finais não mudam; CONFIRMED ainda pode ser cancelado ou comparecer")
    void shouldExposeTransitions() {
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.ATTENDED)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.EXPIRED_UNCONFIRMED)).isFalse();
        assertThat(AppointmentStatus.CONFIRMED.isOpen()).isTrue();
        for (AppointmentStatus target : AppointmentStatus.values()) {
            assertThat(AppointmentStatus.ATTENDED.canTransitionTo(target)).isFalse();
            assertThat(AppointmentStatus.WITHDRAWN.canTransitionTo(target)).isFalse();
        }
    }
}
