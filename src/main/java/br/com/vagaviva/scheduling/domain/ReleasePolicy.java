package br.com.vagaviva.scheduling.domain;

import br.com.vagaviva.scheduling.SlotStatus;
import java.time.Instant;

/**
 * RN-15 — política de vaga liberada (Strategy do SPEC §3): com ≥ 5 dias a vaga volta à alocação
 * regular; entre 2 h e 5 dias vira oferta de encaixe; com menos de 2 h é perdida.
 */
public final class ReleasePolicy {

    private final SlotLeadTimes leadTimes;

    public ReleasePolicy(SlotLeadTimes leadTimes) {
        this.leadTimes = leadTimes;
    }

    public SlotStatus decide(Instant startAt, Instant now) {
        if (!startAt.isBefore(now.plus(leadTimes.regularAllocation()))) {
            return SlotStatus.AVAILABLE;
        }
        if (!startAt.isBefore(now.plus(leadTimes.shortNotice()))) {
            return SlotStatus.OPEN_FOR_OFFERS;
        }
        return SlotStatus.EXPIRED;
    }
}
