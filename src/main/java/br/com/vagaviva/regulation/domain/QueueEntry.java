package br.com.vagaviva.regulation.domain;

import br.com.vagaviva.regulation.RiskClass;
import java.time.Instant;
import java.util.UUID;

/** Dados de um encaminhamento que definem sua posição na fila. */
public record QueueEntry(UUID referralId, RiskClass riskClass, boolean priorityGroup, Instant queueEnteredAt) {
}
