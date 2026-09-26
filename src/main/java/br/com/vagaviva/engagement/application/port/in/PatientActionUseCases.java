package br.com.vagaviva.engagement.application.port.in;

import br.com.vagaviva.scheduling.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** RF-26: ações do paciente pelo link, sem login — o token é a credencial. */
public interface PatientActionUseCases {

    /**
     * @throws br.com.vagaviva.shared.domain.NotFoundException token inexistente
     * @throws br.com.vagaviva.shared.domain.GoneException token expirado
     */
    PatientAppointmentView view(String token, @Nullable String clientIp);

    ActionResult confirm(String token, @Nullable String clientIp);

    ActionResult cancel(String token, @Nullable String clientIp);

    ActionResult withdraw(String token, @Nullable String clientIp);

    enum PatientAction {
        CONFIRM,
        CANCEL,
        WITHDRAW
    }

    /** O que o paciente vê — mínimo (RN-20): primeiro nome, rótulo genérico, data, unidade. */
    record PatientAppointmentView(String kind, String firstName, String label, Instant startAt, String unitName,
            String unitAddress, AppointmentStatus status, @Nullable Instant confirmationDeadline,
            List<PatientAction> allowedActions) {
    }

    record ActionResult(AppointmentStatus status, boolean backToQueue) {
    }
}
