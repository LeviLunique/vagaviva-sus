package br.com.vagaviva.regulation;

import java.util.UUID;

/** Tamanho atual da fila de uma especialidade numa classe de risco. */
public record WaitingCount(UUID specialtyId, RiskClass riskClass, long waiting) {
}
