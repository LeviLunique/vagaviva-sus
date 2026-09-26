package br.com.vagaviva.reallocation.application.service;

import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.CLOCK;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.NOW;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.UNIT;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.openSlot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase.OfferFilter;
import br.com.vagaviva.reallocation.application.port.in.StartOfferCampaignUseCase;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class OfferExpirationAndQueryTest {

    @Mock SlotOfferRepository offers;
    @Mock StartOfferCampaignUseCase campaign;
    @Mock SchedulingApi scheduling;
    @Mock PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("RN-17: expira vaga a vaga e avança a cascata; a falha de uma vaga não impede as outras")
    void shouldExpireSlotBySlot() {
        UUID ok = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(offers.findSlotsWithOverdueOffers(NOW, 100)).thenReturn(List.of(broken, ok));
        lenient().when(offers.expireOverdue(ok, NOW)).thenReturn(2);
        var meters = new SimpleMeterRegistry();
        doThrow(new IllegalStateException("falha")).when(campaign).advance(broken);

        int processed = new OfferExpirationService(offers, campaign,
                new OfferMetrics(meters), transactionManager, CLOCK).expireOverdue();

        assertThat(processed).isEqualTo(1);
        verify(offers).expireOverdue(ok, NOW);
        verify(campaign).advance(ok);
        assertThat(meters.counter("vagaviva.offers", "status", "EXPIRED").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("RF-33: SCHEDULER só consulta vaga da própria unidade; REGULATOR e ADMIN sem restrição")
    void shouldScopeQueries() {
        var queries = new SlotOfferQueryService(offers, scheduling);
        UUID slot = UUID.randomUUID();
        when(scheduling.findSlotView(slot)).thenReturn(Optional.of(openSlot(slot)));
        when(offers.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        var page = PageRequest.of(0, 20);

        queries.list(new OfferFilter(slot, null), page, new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UNIT, "Sônia"));
        queries.list(new OfferFilter(null, null), page, new CurrentUser(UUID.randomUUID(), Role.REGULATOR, null, "Rui"));
        assertThatThrownBy(() -> queries.list(new OfferFilter(slot, null), page,
                new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UUID.randomUUID(), "Outra")))
                .isInstanceOf(ForbiddenOperationException.class).extracting("code").isEqualTo("OFFERS_OUT_OF_UNIT");
        assertThatThrownBy(() -> queries.list(new OfferFilter(null, null), page,
                new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UNIT, "Sônia")))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
