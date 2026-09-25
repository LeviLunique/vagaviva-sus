package br.com.vagaviva.patient.application.port.in;

import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Leitura de paciente (RF-10): toda leitura de dado pessoal é auditada (RF-05). */
public interface ReadPatientUseCase {

    /** @throws br.com.vagaviva.shared.domain.NotFoundException {@code PATIENT_NOT_FOUND} */
    Patient get(UUID patientId, CurrentUser actor, @Nullable String clientIp);

    /** Busca por exatamente um documento — CNS ou CPF — recebido no corpo (RN-22). */
    Patient search(SearchPatientQuery query, CurrentUser actor, @Nullable String clientIp);

    record SearchPatientQuery(@Nullable String cns, @Nullable String cpf) {
    }
}
