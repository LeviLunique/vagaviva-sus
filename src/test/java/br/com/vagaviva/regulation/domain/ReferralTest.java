package br.com.vagaviva.regulation.domain;

import static br.com.vagaviva.regulation.fixtures.ReferralFixture.CLOCK;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.NOW;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.REGULATOR;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.UNIT;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aPendingReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aReturnedReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aScheduledReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aWaitingReferral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.regulation.ReviewReason;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReferralTest {

    private static final Clock LATER = Clock.fixed(NOW.plus(Duration.ofDays(10)), ZoneOffset.UTC);

    @Test
    @DisplayName("RF-12: novo encaminhamento aguarda regulação, com CID normalizado e sem classe de risco")
    void shouldCreatePendingReferral() {
        Referral referral = Referral.create(Protocol.of(2026, 1), UUID.randomUUID(), UUID.randomUUID(), UNIT,
                UUID.randomUUID(), "  Cefaleia crônica.  ", " m54.5 ", false, MunicipalityCode.of("3550308"), CLOCK);

        assertThat(referral.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);
        assertThat(referral.cid10()).isEqualTo("M54.5");
        assertThat(referral.clinicalJustification()).isEqualTo("Cefaleia crônica.");
        assertThat(referral.riskClass()).isNull();
        assertThat(referral.queueEnteredAt()).isNull();
        assertThat(referral.belongsToUnit(UNIT)).isTrue();
        assertThat(referral.belongsToUnit(UUID.randomUUID())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10", "I1", "ABC", "I10.12345"})
    @DisplayName("CID-10 fora do padrão ⇒ INVALID_CID10; justificativa vazia ⇒ CLINICAL_JUSTIFICATION_REQUIRED")
    void shouldValidateClinicalData(String cid) {
        assertThatThrownBy(() -> Referral.create(Protocol.of(2026, 1), UUID.randomUUID(), UUID.randomUUID(), UNIT,
                UUID.randomUUID(), "Justificativa", cid, false, MunicipalityCode.of("3550308"), CLOCK))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_CID10");
        assertThatThrownBy(() -> Referral.create(Protocol.of(2026, 1), UUID.randomUUID(), UUID.randomUUID(), UNIT,
                UUID.randomUUID(), " ", null, false, MunicipalityCode.of("3550308"), CLOCK))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CLINICAL_JUSTIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("RF-13: aprovação define risco, grupo prioritário e entrada na fila agora")
    void shouldApproveIntoQueue() {
        Referral referral = aPendingReferral();

        referral.approve(RiskClass.RED, true, REGULATOR, LATER);

        assertThat(referral.status()).isEqualTo(ReferralStatus.WAITING);
        assertThat(referral.riskClass()).isEqualTo(RiskClass.RED);
        assertThat(referral.priorityGroup()).isTrue();
        assertThat(referral.queueEnteredAt()).isEqualTo(LATER.instant());
        assertThat(referral.regulatedBy()).isEqualTo(REGULATOR);
        assertThat(referral.toQueueEntry()).isEqualTo(new QueueEntry(referral.id(), RiskClass.RED, true, LATER.instant()));
    }

    @Test
    @DisplayName("RF-13/14: devolução exige justificativa; o reenvio corrige os dados e volta à regulação")
    void shouldReturnAndResubmit() {
        Referral referral = aPendingReferral();
        assertThatThrownBy(() -> referral.returnForCorrection(" ", REGULATOR, CLOCK))
                .extracting("code").isEqualTo("RETURN_REASON_REQUIRED");

        referral.returnForCorrection("  Anexar ECG.  ", REGULATOR, CLOCK);
        assertThat(referral.status()).isEqualTo(ReferralStatus.RETURNED);
        assertThat(referral.returnReason()).isEqualTo("Anexar ECG.");

        referral.resubmit("ECG anexado: alteração de ST.", "I20", false, LATER);
        assertThat(referral.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);
        assertThat(referral.clinicalJustification()).isEqualTo("ECG anexado: alteração de ST.");
        assertThat(referral.acceptsShortNotice()).isFalse();

        referral.approve(RiskClass.GREEN, false, REGULATOR, LATER);
        assertThat(referral.returnReason()).isNull();
    }

    @Test
    @DisplayName("RF-15: cancelamento exige motivo; não é possível cancelar agendado")
    void shouldCancel() {
        Referral waiting = aWaitingReferral();
        assertThatThrownBy(() -> waiting.cancel("", CLOCK)).extracting("code").isEqualTo("CANCEL_REASON_REQUIRED");

        waiting.cancel("Duplicado em outro município.", CLOCK);
        assertThat(waiting.status()).isEqualTo(ReferralStatus.CANCELLED);
        assertThat(waiting.cancelReason()).isEqualTo("Duplicado em outro município.");

        assertThatThrownBy(() -> aScheduledReferral().cancel("motivo", CLOCK)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("agendado volta à fila pela unidade preservando a data de entrada (RN-06)")
    void shouldReturnToQueuePreservingEntryWhenUnitCancels() {
        Referral referral = aScheduledReferral();
        var entry = referral.queueEnteredAt();

        referral.returnToQueue(ReturnReason.UNIT_CANCELLED, 2, LATER);

        assertThat(referral.status()).isEqualTo(ReferralStatus.WAITING);
        assertThat(referral.queueEnteredAt()).isEqualTo(entry);
        assertThat(referral.missedConfirmations()).isZero();
        assertThat(referral.scheduledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("RN-13: 1ª não confirmação volta à fila; a 2ª manda para reavaliação do regulador")
    void shouldSendToReviewOnSecondMissedConfirmation() {
        Referral referral = aScheduledReferral();

        referral.returnToQueue(ReturnReason.UNCONFIRMED, 2, CLOCK);
        assertThat(referral.status()).isEqualTo(ReferralStatus.WAITING);
        assertThat(referral.missedConfirmations()).isEqualTo(1);

        referral.markScheduled(CLOCK);
        referral.returnToQueue(ReturnReason.UNCONFIRMED, 2, CLOCK);
        assertThat(referral.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);
        assertThat(referral.missedConfirmations()).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-14: falta envia para reavaliação somando a falta; comparecimento conclui")
    void shouldHandleNoShowAndCompletion() {
        Referral noShow = aScheduledReferral();
        noShow.sendToReview(ReviewReason.NO_SHOW, CLOCK);
        assertThat(noShow.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);
        assertThat(noShow.noShows()).isEqualTo(1);

        Referral attended = aScheduledReferral();
        attended.markCompleted(CLOCK);
        assertThat(attended.status()).isEqualTo(ReferralStatus.COMPLETED);
    }

    @Test
    @DisplayName("o paciente pode desistir enquanto aguarda ou depois de agendado")
    void shouldWithdraw() {
        Referral waiting = aWaitingReferral();
        waiting.withdraw(CLOCK);
        Referral scheduled = aScheduledReferral();
        scheduled.withdraw(CLOCK);

        assertThat(waiting.status()).isEqualTo(ReferralStatus.WITHDRAWN);
        assertThat(scheduled.status()).isEqualTo(ReferralStatus.WITHDRAWN);
    }

    static Stream<Arguments> invalidTransitions() {
        Consumer<Referral> approve = r -> r.approve(RiskClass.RED, false, REGULATOR, CLOCK);
        Consumer<Referral> giveBack = r -> r.returnForCorrection("motivo", REGULATOR, CLOCK);
        Consumer<Referral> resubmit = r -> r.resubmit("justificativa", null, false, CLOCK);
        Consumer<Referral> schedule = r -> r.markScheduled(CLOCK);
        Consumer<Referral> backToQueue = r -> r.returnToQueue(ReturnReason.UNIT_CANCELLED, 2, CLOCK);
        Consumer<Referral> review = r -> r.sendToReview(ReviewReason.NO_SHOW, CLOCK);
        Consumer<Referral> complete = r -> r.markCompleted(CLOCK);
        Consumer<Referral> withdraw = r -> r.withdraw(CLOCK);
        return Stream.of(
                Arguments.of("aprovar já na fila", aWaitingReferral(), approve),
                Arguments.of("devolver já na fila", aWaitingReferral(), giveBack),
                Arguments.of("reenviar pendente", aPendingReferral(), resubmit),
                Arguments.of("agendar pendente", aPendingReferral(), schedule),
                Arguments.of("agendar devolvido", aReturnedReferral(), schedule),
                Arguments.of("voltar à fila sem estar agendado", aWaitingReferral(), backToQueue),
                Arguments.of("reavaliar sem estar agendado", aWaitingReferral(), review),
                Arguments.of("concluir sem estar agendado", aWaitingReferral(), complete),
                Arguments.of("desistir pendente de regulação", aPendingReferral(), withdraw),
                Arguments.of("aprovar devolvido sem reenvio", aReturnedReferral(), approve));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTransitions")
    @DisplayName("transições fora do diagrama ⇒ REFERRAL_INVALID_STATE (409)")
    void shouldRejectInvalidTransitions(String description, Referral referral, Consumer<Referral> action) {
        assertThatThrownBy(() -> action.accept(referral))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("REFERRAL_INVALID_STATE");
    }

    @Test
    @DisplayName("estados finais não saem do lugar; só PENDING, RETURNED, WAITING e SCHEDULED são ativos (RN-05)")
    void shouldKnowActiveAndFinalStates() {
        for (ReferralStatus finalState : new ReferralStatus[] {ReferralStatus.COMPLETED, ReferralStatus.WITHDRAWN,
            ReferralStatus.CANCELLED}) {
            assertThat(finalState.isActive()).isFalse();
            for (ReferralStatus target : ReferralStatus.values()) {
                assertThat(finalState.canTransitionTo(target)).isFalse();
            }
        }
        assertThat(ReferralStatus.SCHEDULED.isActive()).isTrue();
        assertThatThrownBy(() -> aPendingReferral().toQueueEntry()).isInstanceOf(IllegalStateException.class);
    }
}
