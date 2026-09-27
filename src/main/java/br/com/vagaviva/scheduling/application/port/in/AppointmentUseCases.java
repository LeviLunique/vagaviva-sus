package br.com.vagaviva.scheduling.application.port.in;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Agendamentos da unidade (RF-21) e comparecimento (RF-22). */
public interface AppointmentUseCases {

    /** Leitura auditada; SCHEDULER só da própria unidade. */
    Appointment get(UUID appointmentId, CurrentUser actor, @Nullable String clientIp);

    /** SCHEDULER vê só a própria unidade; paciente identificado só por primeiro nome e CNS mascarado. */
    Page<AppointmentItem> list(AppointmentFilter filter, Pageable pageable, CurrentUser actor);

    /** RN-19: no dia do atendimento. */
    Appointment checkIn(UUID appointmentId, CurrentUser actor, @Nullable String clientIp);

    /** RN-19 e RN-14: após o início; o encaminhamento volta à regulação. */
    Appointment markNoShow(UUID appointmentId, CurrentUser actor, @Nullable String clientIp);

    record AppointmentFilter(@Nullable UUID unitId, @Nullable LocalDate date, @Nullable AppointmentStatus status) {
    }

    record AppointmentItem(UUID id, UUID slotId, UUID referralId, UUID patientId, @Nullable String patientFirstName,
            @Nullable String patientCnsMasked, UUID unitId, UUID specialtyId, Instant startAt, AppointmentOrigin origin,
            AppointmentStatus status, @Nullable Instant confirmationDeadline) {
    }
}
