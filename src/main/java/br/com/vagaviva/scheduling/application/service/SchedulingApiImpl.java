package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class SchedulingApiImpl implements SchedulingApi {

    private final AppointmentRepository appointments;

    SchedulingApiImpl(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public Optional<AppointmentView> findAppointmentView(UUID appointmentId) {
        return appointments.findById(appointmentId).map(a -> new AppointmentView(a.id(), a.slotId(), a.referralId(),
                a.patientId(), a.unitId(), a.specialtyId(), a.startAt(), a.origin(), a.status(),
                a.confirmationDeadline()));
    }
}
