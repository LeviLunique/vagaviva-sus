package br.com.vagaviva.scheduling;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública da agenda (Facade). Cresce por fase: ações do paciente e lembretes na F5, encaixe
 * na F6 (SPEC §7.F4).
 */
public interface SchedulingApi {

    Optional<AppointmentView> findAppointmentView(UUID appointmentId);
}
