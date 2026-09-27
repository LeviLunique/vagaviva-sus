package br.com.vagaviva.patient.application.port.in;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** RF-08: cadastro de paciente (CNS obrigatório e único; CPF opcional e único). */
public interface RegisterPatientUseCase {

    Patient register(RegisterPatientCommand command);

    record RegisterPatientCommand(String cns, @Nullable String cpf, String fullName, @Nullable String socialName,
            LocalDate birthDate, String municipalityCode, String phone, ContactChannel preferredChannel,
            boolean whatsappOptIn, boolean pregnant, boolean disability, CurrentUser actor, @Nullable String clientIp) {
    }
}
