package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

public record AppointmentAttended(UUID appointmentId, UUID referralId, UUID unitId, UUID specialtyId, Instant startAt) {
}
