package br.com.vagaviva.reallocation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-32/RN-17: três pacientes aceitam a mesma vaga ao mesmo tempo — exatamente um vence. */
@IntegrationTest
@Import({SchedulingTestData.class, ReallocationTestData.class})
class OfferRaceIT {

    @Autowired ReallocationTestData data;
    @Autowired ReallocationApi reallocation;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("3 aceites simultâneos ⇒ 1 agendamento CONFIRMED (SHORT_NOTICE_OFFER), 2 × 409 SLOT_ALREADY_FILLED")
    void onlyOneAcceptWins() throws Exception {
        UUID specialty = data.specialty();
        List<Referral> queue = List.of(data.shortNoticeReferral(specialty, 30), data.shortNoticeReferral(specialty, 20),
                data.shortNoticeReferral(specialty, 10));
        UUID slot = data.publishSlot(specialty, 26);
        List<UUID> offers = data.awaitPendingOffers(slot, 1, 3);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            for (UUID offer : offers) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        return reallocation.accept(offer);
                    } catch (ConflictException ex) {
                        return ex.code();
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get());
            }

            assertThat(outcomes).filteredOn(ReallocationApi.AcceptedOffer.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn("SLOT_ALREADY_FILLED"::equals).hasSize(2);
        }

        assertThat(data.status("slot", slot)).isEqualTo("ALLOCATED");
        assertThat(jdbc.queryForList("select status from slot_offer where slot_id = ? order by status", String.class,
                slot)).containsExactly("ACCEPTED", "SUPERSEDED", "SUPERSEDED");
        assertThat(jdbc.queryForList("select origin || '/' || status from appointment where slot_id = ?", String.class,
                slot)).containsExactly("SHORT_NOTICE_OFFER/CONFIRMED");
        assertThat(queue.stream().map(r -> data.status("referral", r.id())).toList())
                .containsExactlyInAnyOrder("SCHEDULED", "WAITING", "WAITING");
    }
}
