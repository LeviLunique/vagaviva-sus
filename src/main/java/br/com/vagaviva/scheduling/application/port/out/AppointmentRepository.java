package br.com.vagaviva.scheduling.application.port.out;

import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.SchedulingApi.ReminderType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppointmentRepository {

    Appointment save(Appointment appointment);

    Optional<Appointment> findById(UUID id);

    /** Agendamento ainda aberto (aguardando confirmação ou confirmado) da vaga. */
    Optional<Appointment> findOpenBySlot(UUID slotId);

    Page<Appointment> search(AppointmentFilter filter, Pageable pageable);

    /** Ids dos agendamentos aguardando confirmação com prazo vencido (sem travar). */
    List<UUID> findOverdueIds(Instant now, int limit);

    /** Trava o agendamento se ainda estiver aguardando confirmação ({@code FOR UPDATE SKIP LOCKED}). */
    Optional<Appointment> lockPendingConfirmation(UUID id);

    List<Appointment> findForReminder(ReminderType type, Instant from, Instant to);
}
