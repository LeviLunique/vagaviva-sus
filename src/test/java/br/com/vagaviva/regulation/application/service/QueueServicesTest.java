package br.com.vagaviva.regulation.application.service;

import static br.com.vagaviva.regulation.fixtures.ReferralFixture.CLOCK;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.NOW;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aScheduledReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aWaitingReferral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.regulation.ReviewReason;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.out.ProtocolSequence;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueServicesTest {

    private static final RegulationProperties PROPERTIES =
            new RegulationProperties(Duration.ofMinutes(5), Duration.ofDays(30), 2);

    @Mock ReferralRepository repository;
    @Mock QueueSnapshotRepository snapshots;
    @Mock ProtocolSequence sequence;

    private QueueApiImpl api() {
        lenient().when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        return new QueueApiImpl(repository, PROPERTIES, CLOCK);
    }

    @Test
    @DisplayName("lockNextEligible pede 1 linha na ordem oficial; lockShortNotice filtra quem aceita encaixe")
    void shouldDelegateLocks() {
        UUID specialty = UUID.randomUUID();
        var criteria = new EligibilityCriteria(Set.of("3550308"));
        var candidate = new ReferralCandidate(UUID.randomUUID(), UUID.randomUUID(), specialty, RiskClass.RED, false,
                NOW, true);
        when(repository.lockWaiting(specialty, criteria, false, Set.of(), 1)).thenReturn(List.of(candidate));
        when(repository.lockWaiting(specialty, criteria, true, Set.of(candidate.patientId()), 3)).thenReturn(List.of());

        assertThat(api().lockNextEligible(specialty, criteria)).contains(candidate);
        assertThat(api().lockShortNoticeCandidates(specialty, criteria, Set.of(candidate.patientId()), 3)).isEmpty();
        assertThat(EligibilityCriteria.anyMunicipality().serviceArea()).isEmpty();
        assertThat(new EligibilityCriteria(null).serviceArea()).isEmpty();
    }

    @Test
    @DisplayName("transições pela QueueApi: agendar, voltar à fila (RN-13 com limite configurado), concluir, desistir")
    void shouldApplyTransitions() {
        var queue = api();
        Referral waiting = aWaitingReferral();
        when(repository.findById(waiting.id())).thenReturn(Optional.of(waiting));

        queue.markScheduled(waiting.id());
        assertThat(waiting.status()).isEqualTo(ReferralStatus.SCHEDULED);
        queue.returnToQueue(waiting.id(), ReturnReason.UNCONFIRMED);
        assertThat(waiting.status()).isEqualTo(ReferralStatus.WAITING);
        queue.markScheduled(waiting.id());
        queue.returnToQueue(waiting.id(), ReturnReason.UNCONFIRMED);
        assertThat(waiting.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);

        Referral scheduled = aScheduledReferral();
        when(repository.findById(scheduled.id())).thenReturn(Optional.of(scheduled));
        queue.sendToReview(scheduled.id(), ReviewReason.NO_SHOW);
        assertThat(scheduled.noShows()).isEqualTo(1);

        Referral attended = aScheduledReferral();
        when(repository.findById(attended.id())).thenReturn(Optional.of(attended));
        queue.markCompleted(attended.id());
        assertThat(attended.status()).isEqualTo(ReferralStatus.COMPLETED);

        Referral leaving = aWaitingReferral();
        when(repository.findById(leaving.id())).thenReturn(Optional.of(leaving));
        queue.withdraw(leaving.id());
        assertThat(leaving.status()).isEqualTo(ReferralStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("findView não expõe dados clínicos; inexistente vira vazio; transição em inexistente ⇒ 404")
    void shouldExposeView() {
        var queue = api();
        Referral waiting = aWaitingReferral();
        when(repository.findById(waiting.id())).thenReturn(Optional.of(waiting));

        assertThat(queue.findView(waiting.id())).hasValueSatisfying(view -> {
            assertThat(view.protocol()).isEqualTo(waiting.protocol().value());
            assertThat(view.riskClass()).isEqualTo(RiskClass.YELLOW);
            assertThat(view.patientMunicipalityCode()).isEqualTo("3550308");
        });
        assertThat(queue.findView(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> queue.markScheduled(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("RN-04: protocolo com o ano da data civil em São Paulo (virada de ano) e o sequencial do banco")
    void shouldGenerateProtocolWithBusinessYear() {
        when(sequence.next()).thenReturn(123L);
        // 01/01/2027 01:00 UTC ainda é 31/12/2026 em São Paulo.
        Clock newYearUtc = Clock.fixed(Instant.parse("2027-01-01T01:00:00Z"), ZoneId.of("America/Sao_Paulo"));

        assertThat(new ProtocolGenerator(sequence, newYearUtc).next().value()).isEqualTo("VV-2026-0000123");
    }

    @Test
    @DisplayName("RN-07/08: o snapshot usa a janela de vazão configurada (30 dias)")
    void shouldRefreshSnapshotWithThroughputWindow() {
        when(snapshots.rebuild(NOW, NOW.minus(Duration.ofDays(30)), 30)).thenReturn(5);

        var result = new QueueSnapshotService(snapshots, PROPERTIES, CLOCK).refresh();

        assertThat(result.queued()).isEqualTo(5);
        assertThat(result.snapshotAt()).isEqualTo(NOW);
        verify(snapshots).rebuild(NOW, NOW.minus(Duration.ofDays(30)), 30);
    }
}
