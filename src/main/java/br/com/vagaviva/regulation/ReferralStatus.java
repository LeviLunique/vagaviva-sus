package br.com.vagaviva.regulation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Estados do encaminhamento e transições permitidas (SPEC §5.2 — padrão State leve). */
public enum ReferralStatus {
    PENDING_REGULATION,
    RETURNED,
    WAITING,
    SCHEDULED,
    COMPLETED,
    WITHDRAWN,
    CANCELLED;

    private static final Map<ReferralStatus, Set<ReferralStatus>> TRANSITIONS = Map.of(
            PENDING_REGULATION, EnumSet.of(WAITING, RETURNED, CANCELLED),
            RETURNED, EnumSet.of(PENDING_REGULATION, CANCELLED),
            WAITING, EnumSet.of(SCHEDULED, WITHDRAWN, CANCELLED),
            SCHEDULED, EnumSet.of(WAITING, PENDING_REGULATION, COMPLETED, WITHDRAWN));

    public boolean canTransitionTo(ReferralStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** RN-05: estados que contam como encaminhamento ativo. */
    public boolean isActive() {
        return this == PENDING_REGULATION || this == RETURNED || this == WAITING || this == SCHEDULED;
    }
}
