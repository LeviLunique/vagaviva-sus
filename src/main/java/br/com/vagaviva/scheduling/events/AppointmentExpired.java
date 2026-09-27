package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

/** Prazo de confirmação vencido (RF-27, RN-13). */
public record AppointmentExpired(UUID appointmentId, UUID slotId, UUID referralId, UUID patientId, Instant startAt) {
}
