package br.com.vagaviva.scheduling.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.scheduling.events.SlotsPublished;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

class SlotsPublishedListenerTest {

    @Test
    @DisplayName("publicação de agenda dispara a alocação, como listener de módulo (após o commit, com reentrega)")
    void shouldRunAllocationOnPublication() throws Exception {
        RunAllocationUseCase allocation = mock(RunAllocationUseCase.class);

        new SlotsPublishedListener(allocation).on(new SlotsPublished(UUID.randomUUID(), UUID.randomUUID(), 3));

        verify(allocation).run();
        assertThat(SlotsPublishedListener.class.getDeclaredMethod("on", SlotsPublished.class)
                .isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
    }
}
