package br.com.vagaviva.scheduling.domain;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CLOCK;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.LEAD_TIMES;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.NOW;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.SPECIALTY;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.UNIT;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.anAvailableSlot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlotTest {

    private static Slot publishIn(Duration fromNow) {
        return Slot.publish(UNIT, SPECIALTY, "Dr. Beto Fictício", NOW.plus(fromNow), 20, LEAD_TIMES, CLOCK);
    }

    @Test
    @DisplayName("RN-09: com ≥ 5 dias a vaga entra na alocação regular; entre 2 h e 5 dias vai para o encaixe")
    void shouldChooseInitialStatusByLeadTime() {
        assertThat(publishIn(Duration.ofDays(5)).status()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(publishIn(Duration.ofDays(5).minusMinutes(1)).status()).isEqualTo(SlotStatus.OPEN_FOR_OFFERS);
        assertThat(publishIn(Duration.ofHours(2)).status()).isEqualTo(SlotStatus.OPEN_FOR_OFFERS);
    }

    @Test
    @DisplayName("RF-19 CA1: vaga no passado ⇒ SLOT_IN_PAST; com menos de 2 h ⇒ SLOT_TOO_SOON (RN-09)")
    void shouldRejectPastAndTooSoon() {
        assertThatThrownBy(() -> publishIn(Duration.ZERO)).extracting("code").isEqualTo("SLOT_IN_PAST");
        assertThatThrownBy(() -> publishIn(Duration.ofHours(-1))).extracting("code").isEqualTo("SLOT_IN_PAST");
        assertThatThrownBy(() -> publishIn(Duration.ofMinutes(119))).extracting("code").isEqualTo("SLOT_TOO_SOON");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 241})
    @DisplayName("duração fora de 5–240 minutos ⇒ INVALID_SLOT_DURATION; profissional em branco ⇒ PROFESSIONAL_REQUIRED")
    void shouldValidateDurationAndProfessional(int minutes) {
        Instant start = NOW.plus(Duration.ofDays(10));
        assertThatThrownBy(() -> Slot.publish(UNIT, SPECIALTY, "Dra. X", start, minutes, LEAD_TIMES, CLOCK))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_SLOT_DURATION");
        assertThatThrownBy(() -> Slot.publish(UNIT, SPECIALTY, " ", start, 30, LEAD_TIMES, CLOCK))
                .extracting("code").isEqualTo("PROFESSIONAL_REQUIRED");
    }

    @Test
    @DisplayName("sobreposição só conta para o mesmo profissional (sem diferenciar maiúsculas)")
    void shouldDetectOverlapForSameProfessional() {
        Slot first = Slot.publish(UNIT, SPECIALTY, "Dra. Ana", NOW.plus(Duration.ofDays(10)), 30, LEAD_TIMES, CLOCK);
        Slot overlapping = Slot.publish(UNIT, SPECIALTY, "dra. ana", first.startAt().plus(Duration.ofMinutes(20)), 30,
                LEAD_TIMES, CLOCK);
        Slot adjacent = Slot.publish(UNIT, SPECIALTY, "Dra. Ana", first.endAt(), 30, LEAD_TIMES, CLOCK);
        Slot otherProfessional = Slot.publish(UNIT, SPECIALTY, "Dr. Beto", first.startAt(), 30, LEAD_TIMES, CLOCK);

        assertThat(first.overlaps(overlapping)).isTrue();
        assertThat(first.overlaps(adjacent)).isFalse();
        assertThat(first.overlaps(otherProfessional)).isFalse();
        assertThat(first.endAt()).isEqualTo(first.startAt().plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("ciclo: alocada ⇒ usada ou falta; cancelar vaga final ⇒ SLOT_INVALID_STATE")
    void shouldFollowStateMachine() {
        Slot used = anAvailableSlot();
        used.allocate(CLOCK);
        used.markUsed(CLOCK);
        assertThat(used.status()).isEqualTo(SlotStatus.USED);
        assertThatThrownBy(() -> used.cancel(CLOCK)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_INVALID_STATE");

        Slot missed = anAvailableSlot();
        missed.allocate(CLOCK);
        missed.markMissed(CLOCK);
        assertThat(missed.status()).isEqualTo(SlotStatus.MISSED);

        Slot free = anAvailableSlot();
        assertThatThrownBy(() -> free.markUsed(CLOCK)).isInstanceOf(ConflictException.class);
        free.cancel(CLOCK);
        assertThat(free.status()).isEqualTo(SlotStatus.CANCELLED);
        assertThat(free.wasReleased()).isFalse();
    }

    @Test
    @DisplayName("antecedência do encaixe precisa ser menor que a da alocação regular")
    void shouldValidateLeadTimes() {
        assertThatThrownBy(() -> new SlotLeadTimes(Duration.ofHours(2), Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
        for (SlotStatus finalStatus : new SlotStatus[] {SlotStatus.USED, SlotStatus.MISSED, SlotStatus.EXPIRED,
            SlotStatus.CANCELLED}) {
            for (SlotStatus target : SlotStatus.values()) {
                assertThat(finalStatus.canTransitionTo(target)).isFalse();
            }
        }
    }
}
