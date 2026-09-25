package br.com.vagaviva.patient.application.port.in;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RF-09: atualização parcial do contato (contato desatualizado é causa documentada de falta). */
public interface UpdatePatientContactUseCase {

    Patient update(UpdatePatientContactCommand command);

    record UpdatePatientContactCommand(UUID patientId, @Nullable String phone, @Nullable ContactChannel preferredChannel,
            @Nullable Boolean whatsappOptIn, CurrentUser actor, @Nullable String clientIp) {
    }
}
