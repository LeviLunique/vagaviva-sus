package br.com.vagaviva.scheduling;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Estados do agendamento e transições (SPEC §5.2). */
public enum AppointmentStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    ATTENDED,
    NO_SHOW,
    CANCELLED_BY_PATIENT,
    WITHDRAWN,
    EXPIRED_UNCONFIRMED,
    CANCELLED_BY_UNIT;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = Map.of(
            PENDING_CONFIRMATION, EnumSet.of(CONFIRMED, EXPIRED_UNCONFIRMED, CANCELLED_BY_PATIENT, WITHDRAWN,
                    CANCELLED_BY_UNIT, ATTENDED, NO_SHOW),
            CONFIRMED, EnumSet.of(CANCELLED_BY_PATIENT, WITHDRAWN, CANCELLED_BY_UNIT, ATTENDED, NO_SHOW));

    public boolean canTransitionTo(AppointmentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** Agendamento ainda vigente para a vaga (antes do desfecho). */
    public boolean isOpen() {
        return this == PENDING_CONFIRMATION || this == CONFIRMED;
    }
}
