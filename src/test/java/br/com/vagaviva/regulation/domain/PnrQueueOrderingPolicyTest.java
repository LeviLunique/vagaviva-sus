package br.com.vagaviva.regulation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.regulation.RiskClass;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PnrQueueOrderingPolicyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final QueueOrderingPolicy policy = new PnrQueueOrderingPolicy();

    private static QueueEntry entry(String id, RiskClass risk, boolean priority, int dayOffset) {
        return new QueueEntry(UUID.fromString("00000000-0000-7000-8000-00000000000" + id), risk, priority,
                T0.plusSeconds(86_400L * dayOffset));
    }

    @Test
    @DisplayName("RN-06: risco vem antes de tudo — RED recente passa na frente de BLUE antigo e prioritário")
    void riskComesFirst() {
        QueueEntry oldBluePriority = entry("1", RiskClass.BLUE, true, 0);
        QueueEntry newRed = entry("2", RiskClass.RED, false, 30);

        assertThat(sorted(oldBluePriority, newRed)).containsExactly(newRed, oldBluePriority);
    }

    @Test
    @DisplayName("RN-06: no mesmo risco, grupo prioritário passa na frente mesmo tendo entrado depois")
    void priorityGroupBeforeEntryDate() {
        QueueEntry oldRegular = entry("1", RiskClass.YELLOW, false, 0);
        QueueEntry newPriority = entry("2", RiskClass.YELLOW, true, 10);

        assertThat(sorted(oldRegular, newPriority)).containsExactly(newPriority, oldRegular);
    }

    @Test
    @DisplayName("RN-06: mesmo risco e mesma condição ⇒ quem entrou antes; empate total ⇒ id (UUIDv7)")
    void entryDateThenIdBreakTies() {
        QueueEntry first = entry("1", RiskClass.GREEN, false, 1);
        QueueEntry second = entry("2", RiskClass.GREEN, false, 2);
        QueueEntry sameTimeAsSecondButLaterId = entry("3", RiskClass.GREEN, false, 2);

        assertThat(sorted(sameTimeAsSecondButLaterId, second, first))
                .containsExactly(first, second, sameTimeAsSecondButLaterId);
    }

    @Test
    @DisplayName("ordem completa: RED > YELLOW > GREEN > BLUE")
    void fullRiskOrder() {
        List<QueueEntry> entries = List.of(entry("4", RiskClass.BLUE, false, 0), entry("3", RiskClass.GREEN, false, 0),
                entry("2", RiskClass.YELLOW, false, 0), entry("1", RiskClass.RED, false, 0));

        assertThat(sorted(entries.toArray(QueueEntry[]::new))).extracting(QueueEntry::riskClass)
                .containsExactly(RiskClass.RED, RiskClass.YELLOW, RiskClass.GREEN, RiskClass.BLUE);
    }

    private List<QueueEntry> sorted(QueueEntry... entries) {
        List<QueueEntry> list = new ArrayList<>(List.of(entries));
        list.sort(policy.comparator());
        return list;
    }
}
