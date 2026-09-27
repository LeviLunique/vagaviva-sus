package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

/** Agendamento cancelado pela unidade, pelo paciente ("não posso ir") ou por desistência. */
public record AppointmentCancelled(UUID appointmentId, UUID slotId, UUID referralId, UUID patientId, Instant startAt,
        Reason reason) {

    public enum Reason {
        UNIT,
        PATIENT,
        WITHDRAWN
    }
}
