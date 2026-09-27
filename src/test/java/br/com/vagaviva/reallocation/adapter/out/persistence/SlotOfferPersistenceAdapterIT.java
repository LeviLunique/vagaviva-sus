package br.com.vagaviva.reallocation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase.OfferFilter;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class SlotOfferPersistenceAdapterIT {

    @Autowired SlotOfferRepository offers;
    @Autowired Clock clock;

    private SlotOffer offer(UUID slot, int round) {
        return offers.save(SlotOffer.create(slot, UUID.randomUUID(), UUID.randomUUID(), round,
                clock.instant().plus(Duration.ofHours(4)), clock));
    }

    @Test
    @DisplayName("RF-33: filtros por vaga e status, em ordem de rodada; exclusões e última rodada")
    void shouldSearchByRoundAndStatus() {
        UUID slot = UUID.randomUUID();
        SlotOffer second = offer(slot, 2);
        SlotOffer first = offer(slot, 1);
        first.decline(clock);
        offers.save(first);

        var bySlot = offers.search(new OfferFilter(slot, null), PageRequest.of(0, 10));
        var pending = offers.search(new OfferFilter(slot, OfferStatus.PENDING), PageRequest.of(0, 10));

        assertThat(bySlot.getContent()).extracting(SlotOffer::id).containsExactly(first.id(), second.id());
        assertThat(pending.getContent()).extracting(SlotOffer::id).containsExactly(second.id());
        assertThat(offers.findLastRound(slot)).isEqualTo(2);
        assertThat(offers.findLastRound(UUID.randomUUID())).isZero();
        assertThat(offers.findPatientsToExclude(slot)).contains(first.patientId(), second.patientId());
        assertThat(offers.existsPending(slot)).isTrue();
    }
}
