package br.com.vagaviva.scheduling.events;

import java.time.Instant;
import java.util.UUID;

/** Falta (RN-14): o encaminhamento volta para reavaliação do regulador. */
public record AppointmentMissed(UUID appointmentId, UUID referralId, UUID unitId, UUID specialtyId, Instant startAt) {
}
