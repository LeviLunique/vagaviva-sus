package br.com.vagaviva.scheduling;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Visão do agendamento para outros módulos (mensagens, ações do paciente). */
public record AppointmentView(UUID id, UUID slotId, UUID referralId, UUID patientId, UUID unitId, UUID specialtyId,
        Instant startAt, AppointmentOrigin origin, AppointmentStatus status, @Nullable Instant confirmationDeadline) {
}
