package br.com.vagaviva.insights.application.port.in;

/** Atualiza o gauge {@code vagaviva.queue.waiting{specialty}} com a fila atual. */
public interface RefreshQueueGaugeUseCase {

    void refresh();
}
