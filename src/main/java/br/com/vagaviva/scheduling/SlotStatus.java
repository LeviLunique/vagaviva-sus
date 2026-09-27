package br.com.vagaviva.scheduling;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Estados da vaga e transições (SPEC §5.2). */
public enum SlotStatus {
    AVAILABLE,
    ALLOCATED,
    OPEN_FOR_OFFERS,
    USED,
    MISSED,
    EXPIRED,
    CANCELLED;

    private static final Map<SlotStatus, Set<SlotStatus>> TRANSITIONS = Map.of(
            AVAILABLE, EnumSet.of(ALLOCATED, EXPIRED, CANCELLED),
            ALLOCATED, EnumSet.of(USED, MISSED, AVAILABLE, OPEN_FOR_OFFERS, EXPIRED, CANCELLED),
            OPEN_FOR_OFFERS, EnumSet.of(ALLOCATED, EXPIRED, CANCELLED));

    public boolean canTransitionTo(SlotStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
