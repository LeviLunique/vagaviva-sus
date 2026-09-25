package br.com.vagaviva.scheduling.application.port.out;

import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.domain.Appointment;
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
}
