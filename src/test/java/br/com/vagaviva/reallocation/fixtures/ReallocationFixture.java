package br.com.vagaviva.reallocation.fixtures;

import br.com.vagaviva.reallocation.domain.OfferRoundPolicy;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.SlotView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Dados fictícios do encaixe: agora = 26/09/2026 09:00 (São Paulo); vaga amanhã às 11:00. */
public final class ReallocationFixture {

    public static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    public static final Instant START = NOW.plus(Duration.ofHours(26));
    public static final UUID UNIT = UUID.fromString("0199c0de-0000-7000-8000-000000000201");
    public static final UUID SPECIALTY = UUID.fromString("0199c0de-0000-7000-8000-000000000301");
    public static final OfferRoundPolicy POLICY = new OfferRoundPolicy(3, 5, Duration.ofHours(4), Duration.ofHours(2));

    private ReallocationFixture() {
    }

    public static SlotView openSlot(UUID slotId) {
        return new SlotView(slotId, UNIT, SPECIALTY, START, SlotStatus.OPEN_FOR_OFFERS);
    }

    public static SlotOffer pendingOffer(UUID slotId, int round) {
        return SlotOffer.create(slotId, UUID.randomUUID(), UUID.randomUUID(), round, NOW.plus(Duration.ofHours(4)), CLOCK);
    }

    public static Clock at(Instant instant) {
        return Clock.fixed(instant, CLOCK.getZone());
    }
}
