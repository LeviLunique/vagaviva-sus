package br.com.vagaviva.regulation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.PnrQueueOrderingPolicy;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.QueueEntry;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** A ordem do SQL da fila é exatamente a da política RN-06, e o SKIP LOCKED não entrega o mesmo paciente duas vezes. */
@IntegrationTest
class ReferralQueueQueryIT {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired ReferralRepository referrals;
    @Autowired QueueApi queueApi;
    @Autowired TransactionTemplate transactions;

    @Test
    @DisplayName("ordem SQL da fila = ordem da PnrQueueOrderingPolicy, com 40 encaminhamentos aleatórios")
    void sqlOrderMatchesDomainPolicy() {
        UUID specialty = UUID.randomUUID();
        Random random = new Random(2026);
        List<QueueEntry> expected = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            RiskClass risk = RiskClass.values()[random.nextInt(4)];
            // poucas datas distintas ⇒ muitos empates exercitam o desempate por id
            Instant entry = BASE.plusSeconds(3_600L * random.nextInt(5));
            Referral referral = waiting(specialty, risk, random.nextBoolean(), entry, "3550308", random.nextBoolean());
            expected.add(referral.toQueueEntry());
        }
        expected.sort(new PnrQueueOrderingPolicy().comparator());

        List<UUID> sqlOrder = referrals.findQueue(specialty, PageRequest.of(0, 100)).map(Referral::id).getContent();
        List<UUID> lockedOrder = transactions.execute(status ->
                referrals.lockWaiting(specialty, EligibilityCriteria.anyMunicipality(), false, Set.of(), 100)
                        .stream().map(ReferralCandidate::referralId).toList());

        List<UUID> policyOrder = expected.stream().map(QueueEntry::referralId).toList();
        assertThat(sqlOrder).containsExactlyElementsOf(policyOrder);
        assertThat(lockedOrder).containsExactlyElementsOf(policyOrder);
    }

    @Test
    @DisplayName("RN-10: só elegíveis da área de atendimento; encaixe só para quem aceita e fora dos excluídos")
    void shouldFilterByServiceAreaAndShortNotice() {
        UUID specialty = UUID.randomUUID();
        Referral guarulhos = waiting(specialty, RiskClass.RED, false, BASE, "3518800", true);
        Referral saoPauloNoShortNotice = waiting(specialty, RiskClass.RED, false, BASE.plusSeconds(1), "3550308", false);
        Referral saoPauloShortNotice = waiting(specialty, RiskClass.YELLOW, false, BASE, "3550308", true);

        Optional<ReferralCandidate> next = transactions.execute(status ->
                queueApi.lockNextEligible(specialty, new EligibilityCriteria(Set.of("3550308"))));
        List<ReferralCandidate> shortNotice = transactions.execute(status -> queueApi.lockShortNoticeCandidates(
                specialty, EligibilityCriteria.anyMunicipality(), Set.of(guarulhos.patientId()), 5));

        assertThat(next).map(ReferralCandidate::referralId).contains(saoPauloNoShortNotice.id());
        assertThat(shortNotice).extracting(ReferralCandidate::referralId).containsExactly(saoPauloShortNotice.id());
        assertThat(shortNotice.getFirst().acceptsShortNotice()).isTrue();
    }

    @Test
    @DisplayName("FOR UPDATE SKIP LOCKED: duas transações simultâneas pegam pacientes diferentes, sem esperar")
    void concurrentTransactionsSkipLockedRows() throws Exception {
        UUID specialty = UUID.randomUUID();
        Referral first = waiting(specialty, RiskClass.RED, false, BASE, "3550308", false);
        Referral second = waiting(specialty, RiskClass.RED, false, BASE.plusSeconds(60), "3550308", false);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<UUID> holder = CompletableFuture.supplyAsync(() -> transactions.execute(status -> {
            UUID locked = queueApi.lockNextEligible(specialty, EligibilityCriteria.anyMunicipality())
                    .orElseThrow().referralId();
            firstLocked.countDown();
            await(release);
            return locked;
        }));
        assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();

        UUID secondPick = transactions.execute(status -> queueApi.lockNextEligible(specialty,
                EligibilityCriteria.anyMunicipality()).orElseThrow().referralId());
        release.countDown();

        assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo(first.id());
        assertThat(secondPick).isEqualTo(second.id());
    }

    @Test
    @DisplayName("lock fora de transação é recusado (a trava só faz sentido dentro da transação de quem aloca)")
    void lockRequiresTransaction() {
        assertThatThrownBy(() -> queueApi.lockNextEligible(UUID.randomUUID(), EligibilityCriteria.anyMunicipality()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("RN-05 no banco: índice parcial impede dois ativos do mesmo paciente na especialidade")
    void activeIndexPreventsDuplicates() {
        UUID specialty = UUID.randomUUID();
        Referral first = waiting(specialty, RiskClass.GREEN, false, BASE, "3550308", false);
        Referral twin = Referral.create(nextProtocol(), first.patientId(), specialty, UUID.randomUUID(), UUID.randomUUID(),
                "Outro", null, false, MunicipalityCode.of("3550308"), Clock.fixed(BASE, ZoneOffset.UTC));

        assertThat(referrals.existsActive(first.patientId(), specialty)).isTrue();
        assertThatThrownBy(() -> referrals.save(twin))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("REFERRAL_DUPLICATED");
    }

    private Referral waiting(UUID specialty, RiskClass risk, boolean priority, Instant entry, String municipality,
            boolean acceptsShortNotice) {
        Clock clock = Clock.fixed(entry, ZoneOffset.UTC);
        Referral referral = Referral.create(nextProtocol(), UUID.randomUUID(), specialty, UUID.randomUUID(),
                UUID.randomUUID(), "Justificativa", null, acceptsShortNotice, MunicipalityCode.of(municipality), clock);
        referral.approve(risk, priority, UUID.randomUUID(), clock);
        return referrals.save(referral);
    }

    private static Protocol nextProtocol() {
        return Protocol.of(2099, SEQUENCE.incrementAndGet());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
