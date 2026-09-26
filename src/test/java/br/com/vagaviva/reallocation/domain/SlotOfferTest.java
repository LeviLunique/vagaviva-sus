package br.com.vagaviva.reallocation.domain;

import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.CLOCK;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.NOW;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.at;
import static br.com.vagaviva.reallocation.fixtures.ReallocationFixture.pendingOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.GoneException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotOfferTest {

    @Test
    @DisplayName("nasce PENDING, na rodada informada, com prazo de resposta")
    void shouldStartPending() {
        SlotOffer offer = pendingOffer(UUID.randomUUID(), 2);

        assertThat(offer.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.round()).isEqualTo(2);
        assertThat(offer.createdAt()).isEqualTo(NOW);
        assertThat(offer.respondedAt()).isNull();
    }

    @Test
    @DisplayName("RN-17: aceitar dentro do prazo ⇒ ACCEPTED com horário da resposta")
    void shouldAccept() {
        SlotOffer offer = pendingOffer(UUID.randomUUID(), 1);

        offer.accept(at(NOW.plus(Duration.ofHours(1))));

        assertThat(offer.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(offer.respondedAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("prazo vencido (inclusive no instante exato) ⇒ 410 OFFER_EXPIRED")
    void shouldRejectExpired() {
        SlotOffer offer = pendingOffer(UUID.randomUUID(), 1);

        assertThat(offer.isExpired(offer.expiresAt())).isTrue();
        assertThat(offer.isExpired(offer.expiresAt().minusSeconds(1))).isFalse();
        assertThatThrownBy(() -> offer.accept(at(offer.expiresAt()))).isInstanceOf(GoneException.class)
                .extracting("code").isEqualTo("OFFER_EXPIRED");
        assertThatThrownBy(() -> offer.decline(at(offer.expiresAt()))).isInstanceOf(GoneException.class);
    }

    @Test
    @DisplayName("recusar é idempotente; oferta respondida não aceita de novo; substituída ⇒ SLOT_ALREADY_FILLED")
    void shouldGuardAnsweredOffers() {
        SlotOffer declined = pendingOffer(UUID.randomUUID(), 1);
        assertThat(declined.decline(CLOCK)).isTrue();
        assertThat(declined.decline(CLOCK)).isFalse();
        assertThat(declined.status()).isEqualTo(OfferStatus.DECLINED);
        assertThatThrownBy(() -> declined.accept(CLOCK)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("OFFER_NOT_PENDING");

        SlotOffer superseded = SlotOffer.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, OfferStatus.SUPERSEDED, NOW.plus(Duration.ofHours(4)), NOW, NOW);
        assertThatThrownBy(() -> superseded.accept(CLOCK)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_ALREADY_FILLED");
    }
}
