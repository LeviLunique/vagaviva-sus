package br.com.vagaviva.regulation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository.SpecialtyQueueStats;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import br.com.vagaviva.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RN-07/RN-08: posições e estatísticas do snapshot a partir dos encaminhamentos reais. */
@IntegrationTest
class QueueSnapshotJobIT {

    private static final AtomicLong SEQUENCE = new AtomicLong(5_000_000 + System.nanoTime() % 1_000_000);

    @Autowired ReferralRepository referrals;
    @Autowired QueueApi queueApi;
    @Autowired QueueSnapshotUseCase snapshot;
    @Autowired QueueSnapshotRepository snapshots;

    @Test
    @DisplayName("posição por especialidade na ordem RN-06, total na fila, contagem por risco, vazão e espera média")
    void shouldRebuildPositionsAndStats() {
        UUID specialty = UUID.randomUUID();
        Instant now = Instant.now();
        Referral green = waiting(specialty, RiskClass.GREEN, false, now.minus(Duration.ofDays(20)));
        Referral red = waiting(specialty, RiskClass.RED, false, now.minus(Duration.ofDays(1)));
        Referral yellowPriority = waiting(specialty, RiskClass.YELLOW, true, now.minus(Duration.ofDays(2)));
        Referral scheduledTwoDaysAfterEntry = waiting(specialty, RiskClass.GREEN, false, now.minus(Duration.ofDays(6)));
        // agendado "há 4 dias" = 2 dias após entrar na fila
        Referral reloaded = referrals.findById(scheduledTwoDaysAfterEntry.id()).orElseThrow();
        reloaded.markScheduled(Clock.fixed(now.minus(Duration.ofDays(4)), ZoneOffset.UTC));
        referrals.save(reloaded);

        var result = snapshot.refresh();

        assertThat(result.queued()).isGreaterThanOrEqualTo(3);
        assertThat(snapshots.findPosition(red.id())).hasValueSatisfying(p -> {
            assertThat(p.position()).isEqualTo(1);
            assertThat(p.totalInQueue()).isEqualTo(3);
            assertThat(p.snapshotAt()).isEqualTo(result.snapshotAt());
        });
        assertThat(snapshots.findPosition(yellowPriority.id())).hasValueSatisfying(p -> assertThat(p.position()).isEqualTo(2));
        assertThat(snapshots.findPosition(green.id())).hasValueSatisfying(p -> assertThat(p.position()).isEqualTo(3));
        assertThat(snapshots.findPosition(scheduledTwoDaysAfterEntry.id())).isEmpty();

        SpecialtyQueueStats stats = snapshots.findStats(specialty).orElseThrow();
        assertThat(stats.waitingRed()).isEqualTo(1);
        assertThat(stats.waitingYellow()).isEqualTo(1);
        assertThat(stats.waitingGreen()).isEqualTo(1);
        assertThat(stats.waitingBlue()).isZero();
        assertThat(stats.totalWaiting()).isEqualTo(3);
        assertThat(stats.throughputPerDay()).isEqualByComparingTo(new BigDecimal("0.03"));
        assertThat(stats.avgWaitDays()).isEqualByComparingTo(new BigDecimal("2.0"));
        assertThat(snapshots.findAllStats()).extracting(SpecialtyQueueStats::specialtyId).contains(specialty);
    }

    @Test
    @DisplayName("especialidade sem agendamentos na janela ⇒ vazão e espera média indisponíveis (nulas)")
    void shouldLeaveThroughputEmptyWithoutSchedules() {
        UUID specialty = UUID.randomUUID();
        waiting(specialty, RiskClass.BLUE, false, Instant.now().minus(Duration.ofDays(3)));

        snapshot.refresh();

        SpecialtyQueueStats stats = snapshots.findStats(specialty).orElseThrow();
        assertThat(stats.throughputPerDay()).isNull();
        assertThat(stats.avgWaitDays()).isNull();
        assertThat(stats.waitingBlue()).isEqualTo(1);
    }

    private Referral waiting(UUID specialty, RiskClass risk, boolean priority, Instant entry) {
        Clock clock = Clock.fixed(entry, ZoneOffset.UTC);
        Referral referral = Referral.create(Protocol.of(2098, SEQUENCE.incrementAndGet()), UUID.randomUUID(), specialty,
                UUID.randomUUID(), UUID.randomUUID(), "Justificativa", null, false, MunicipalityCode.of("3550308"), clock);
        referral.approve(risk, priority, UUID.randomUUID(), clock);
        return referrals.save(referral);
    }
}
