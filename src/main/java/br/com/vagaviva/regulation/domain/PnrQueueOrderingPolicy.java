package br.com.vagaviva.regulation.domain;

import java.util.Comparator;

/**
 * RN-06 (PNR-SUS art. 26 — risco, vulnerabilidade e tempo): 1º classe de risco, 2º grupo
 * prioritário antes, 3º entrada na fila mais antiga; o id (UUIDv7, ordenável no tempo) desempata.
 * A mesma ordem está no SQL da fila e do snapshot ({@code risk_rank, priority_group DESC,
 * queue_entered_at, id}); o {@code ReferralQueueQueryIT} garante que as duas não divergem.
 */
public final class PnrQueueOrderingPolicy implements QueueOrderingPolicy {

    private static final Comparator<QueueEntry> ORDER = Comparator
            .comparingInt((QueueEntry entry) -> entry.riskClass().rank())
            .thenComparing(QueueEntry::priorityGroup, Comparator.reverseOrder())
            .thenComparing(QueueEntry::queueEnteredAt)
            .thenComparing(QueueEntry::referralId);

    @Override
    public Comparator<QueueEntry> comparator() {
        return ORDER;
    }
}
