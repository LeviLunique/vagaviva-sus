package br.com.vagaviva.scheduling.events;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import java.time.Instant;
import java.util.UUID;

/** Paciente agendado pela fila: o engajamento (F5) envia a mensagem com o link de confirmação. */
public record AppointmentScheduled(UUID appointmentId, UUID slotId, UUID referralId, UUID patientId, UUID unitId,
        UUID specialtyId, Instant startAt, AppointmentOrigin origin, Instant confirmationDeadline,
        Instant queueEnteredAt) {
}
