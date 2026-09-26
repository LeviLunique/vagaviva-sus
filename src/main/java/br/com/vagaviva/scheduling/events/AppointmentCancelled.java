package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

/** Agendamento cancelado; {@code reason} diz quem cancelou (a unidade, na F4). */
public record AppointmentCancelled(UUID appointmentId, UUID slotId, UUID referralId, UUID patientId, Instant startAt,
        Reason reason) {

    public enum Reason {
        UNIT
    }
}
