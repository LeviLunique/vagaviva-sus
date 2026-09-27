package br.com.vagaviva.regulation.domain;

import java.util.Comparator;

/**
 * Strategy da ordem da fila. A implementação oficial é a {@link PnrQueueOrderingPolicy}; um ente
 * com regra local diferente troca a estratégia sem alterar o restante do módulo (OCP).
 */
public interface QueueOrderingPolicy {

    Comparator<QueueEntry> comparator();
}
