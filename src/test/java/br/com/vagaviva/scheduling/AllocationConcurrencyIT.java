package br.com.vagaviva.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase.AllocationRunResult;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-20 CA1: várias instâncias alocando ao mesmo tempo nunca repetem vaga nem paciente. */
@IntegrationTest
@Import(SchedulingTestData.class)
class AllocationConcurrencyIT {

    private static final int INSTANCES = 6;
    private static final int SLOTS = 25;
    private static final int WAITING = 18;

    @Autowired RunAllocationUseCase allocation;
    @Autowired SlotRepository slots;
    @Autowired SchedulingTestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    @DisplayName("6 execuções simultâneas, 25 vagas e 18 na fila ⇒ 18 agendamentos, sem vaga ou encaminhamento repetido")
    void concurrentRunsNeverDuplicate() throws Exception {
        UUID specialty = UUID.randomUUID();
        UUID unit = data.specializedUnit(Set.of("3550308"));
        Instant now = clock.instant();
        for (int i = 0; i < WAITING; i++) {
            data.waitingReferral(specialty, RiskClass.values()[i % 4], now.minus(Duration.ofDays(30 - i)));
        }
        List<Slot> batch = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            batch.add(Slot.publish(unit, specialty, "Dr. Concorrência", now.plus(Duration.ofDays(8)).plus(Duration.ofMinutes(30L * i)),
                    30, new br.com.vagaviva.scheduling.domain.SlotLeadTimes(Duration.ofDays(5), Duration.ofHours(2)), clock));
        }
        slots.saveAll(batch);

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<AllocationRunResult>> runs = new ArrayList<>();
        for (int i = 0; i < INSTANCES; i++) {
            runs.add(() -> {
                start.await();
                return allocation.run();
            });
        }
        ExecutorService executor = Executors.newFixedThreadPool(INSTANCES);
        List<Future<AllocationRunResult>> futures = new ArrayList<>();
        for (Callable<AllocationRunResult> run : runs) {
            futures.add(executor.submit(run));
        }
        start.countDown();
        int allocatedTotal = 0;
        for (Future<AllocationRunResult> future : futures) {
            allocatedTotal += future.get().allocated();
        }
        executor.shutdown();

        long appointments = count("select count(*) from appointment where specialty_id = ?", specialty);
        assertThat(appointments).isEqualTo(WAITING);
        assertThat(allocatedTotal).isGreaterThanOrEqualTo(WAITING);
        assertThat(count("select count(distinct slot_id) from appointment where specialty_id = ?", specialty)).isEqualTo(WAITING);
        assertThat(count("select count(distinct referral_id) from appointment where specialty_id = ?", specialty)).isEqualTo(WAITING);
        assertThat(count("select count(*) from slot where specialty_id = ? and status = 'ALLOCATED'", specialty)).isEqualTo(WAITING);
        assertThat(count("select count(*) from referral where specialty_id = ? and status = 'SCHEDULED'", specialty)).isEqualTo(WAITING);
        assertThat(count("select count(*) from referral where specialty_id = ? and status = 'WAITING'", specialty)).isZero();
    }

    private long count(String sql, UUID specialty) {
        return jdbc.queryForObject(sql, Long.class, specialty);
    }
}
