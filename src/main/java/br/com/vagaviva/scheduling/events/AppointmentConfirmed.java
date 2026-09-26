package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

public record AppointmentConfirmed(UUID appointmentId, UUID referralId, UUID unitId, UUID specialtyId, Instant startAt) {
}
