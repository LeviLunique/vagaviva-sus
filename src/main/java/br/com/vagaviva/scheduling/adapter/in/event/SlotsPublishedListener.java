package br.com.vagaviva.scheduling.adapter.in.event;

import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.scheduling.events.SlotsPublished;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * RF-20: aloca logo após a publicação de uma agenda — depois do commit, de forma assíncrona e com
 * reentrega garantida pelo registro de eventos (se a aplicação cair, o evento é reprocessado).
 */
@Component
class SlotsPublishedListener {

    private final RunAllocationUseCase allocation;

    SlotsPublishedListener(RunAllocationUseCase allocation) {
        this.allocation = allocation;
    }

    @ApplicationModuleListener
    void on(SlotsPublished event) {
        allocation.run();
    }
}
