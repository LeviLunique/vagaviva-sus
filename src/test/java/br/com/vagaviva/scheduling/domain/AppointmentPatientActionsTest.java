package br.com.vagaviva.scheduling.domain;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CLOCK;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.aPendingAppointment;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.anAvailableSlot;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppointmentPatientActionsTest {

    /** Vaga em 05/10 08:00 (SP); prazo de confirmação 02/10 23:59 (SP) = 03/10 02:59Z. */
    private static final Instant DEADLINE = Instant.parse("2026-10-03T02:59:00Z");

    @Test
    @DisplayName("RN-12: confirma até o prazo; confirmar de novo não muda nada (idempotente)")
    void shouldConfirmIdempotently() {
        Appointment appointment = aPendingAppointment();

        assertThat(appointment.confirm(at(DEADLINE))).isTrue();
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.confirmedAt()).isEqualTo(DEADLINE);
        assertThat(appointment.confirm(at(DEADLINE.plusSeconds(60)))).isFalse();
    }

    @Test
    @DisplayName("RN-12: depois do prazo não confirma mais (422)")
    void shouldRejectConfirmationAfterDeadline() {
        assertThatThrownBy(() -> aPendingAppointment().confirm(at(DEADLINE.plusSeconds(1))))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CONFIRMATION_DEADLINE_PASSED");
    }

    @Test
    @DisplayName("RN-12: cancelar ou desistir até o início, mesmo depois de confirmado; repetir é idempotente")
    void shouldCancelOrWithdrawBeforeStart() {
        Appointment cancelled = aPendingAppointment();
        cancelled.confirm(CLOCK);
        assertThat(cancelled.cancelByPatient(CLOCK)).isTrue();
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED_BY_PATIENT);
        assertThat(cancelled.cancelledAt()).isEqualTo(CLOCK.instant());
        assertThat(cancelled.cancelByPatient(CLOCK)).isFalse();

        Appointment withdrawn = aPendingAppointment();
        assertThat(withdrawn.withdraw(CLOCK)).isTrue();
        assertThat(withdrawn.withdraw(CLOCK)).isFalse();
        assertThat(withdrawn.status()).isEqualTo(AppointmentStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("após o início não cancela (422); ação incompatível com o estado ⇒ 409")
    void shouldGuardAfterStartAndIncompatibleStates() {
        Appointment started = aPendingAppointment();
        assertThatThrownBy(() -> started.cancelByPatient(at(started.startAt())))
                .extracting("code").isEqualTo("APPOINTMENT_ALREADY_STARTED");

        Appointment cancelled = aPendingAppointment();
        cancelled.cancelByPatient(CLOCK);
        assertThatThrownBy(() -> cancelled.withdraw(CLOCK)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("APPOINTMENT_INVALID_STATE");
        assertThatThrownBy(() -> cancelled.confirm(CLOCK)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("RF-27: expira só depois do prazo; antecipar o prazo (demonstração) só se ainda pendente")
    void shouldExpireOnlyAfterDeadline() {
        Appointment appointment = aPendingAppointment();
        assertThatThrownBy(() -> appointment.expireUnconfirmed(at(DEADLINE)))
                .extracting("code").isEqualTo("CONFIRMATION_DEADLINE_NOT_PASSED");

        appointment.anticipateDeadline(CLOCK.instant().minusSeconds(1));
        appointment.expireUnconfirmed(CLOCK);
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.EXPIRED_UNCONFIRMED);
        assertThatThrownBy(() -> appointment.anticipateDeadline(CLOCK.instant()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("RN-15: só vaga alocada é liberada; cada liberação conta (a próxima alocação sai REALLOCATED)")
    void shouldReleaseAllocatedSlotOnly() {
        Slot slot = anAvailableSlot();
        assertThatThrownBy(() -> slot.release(SlotStatus.AVAILABLE, CLOCK)).isInstanceOf(ConflictException.class);

        slot.allocate(CLOCK);
        slot.release(SlotStatus.AVAILABLE, CLOCK);

        assertThat(slot.status()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slot.releaseCount()).isEqualTo(1);
        assertThat(slot.wasReleased()).isTrue();
    }
}
