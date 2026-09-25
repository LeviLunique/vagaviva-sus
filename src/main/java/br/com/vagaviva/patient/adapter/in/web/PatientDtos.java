package br.com.vagaviva.patient.adapter.in.web;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.domain.Patient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs da API de pacientes. */
final class PatientDtos {

    private PatientDtos() {
    }

    record RegisterPatientRequest(
            @Schema(description = "Cartão Nacional de Saúde (15 dígitos)", example = "115881399860000") @NotBlank @Size(max = 20) String cns,
            @Schema(description = "Opcional", example = "68469788019") @Size(max = 14) String cpf,
            @Schema(example = "Maria da Silva") @NotBlank @Size(max = 160) String fullName,
            @Schema(description = "Nome social (usado nas mensagens quando informado)") @Size(max = 160) String socialName,
            @Schema(example = "1958-03-14") @NotNull @Past LocalDate birthDate,
            @Schema(description = "Município de residência (IBGE)", example = "3550308") @NotBlank @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (IBGE)") String municipalityCode,
            @Schema(description = "Celular em E.164", example = "+5511999990001") @NotBlank @Size(max = 25) String phone,
            @NotNull ContactChannel preferredChannel,
            @Schema(description = "Aceite para mensagens por WhatsApp (padrão false)") Boolean whatsappOptIn,
            @Schema(description = "Gestante (padrão false)") Boolean pregnant,
            @Schema(description = "Pessoa com deficiência (padrão false)") Boolean disability) {
    }

    record SearchPatientRequest(
            @Schema(example = "115881399860000") @Size(max = 20) String cns,
            @Schema(description = "Informe CNS ou CPF — exatamente um") @Size(max = 14) String cpf) {
    }

    record UpdateContactRequest(
            @Schema(example = "+5511988887777") @Size(max = 25) String phone,
            ContactChannel preferredChannel,
            Boolean whatsappOptIn) {
    }

    /** RN-21: CNS e CPF sempre mascarados. */
    record PatientResponse(UUID id, String cnsMasked, String cpfMasked, String fullName, String socialName,
            LocalDate birthDate, String municipalityCode, String phone, ContactChannel preferredChannel,
            boolean whatsappOptIn, boolean pregnant, boolean disability, boolean priorityGroup, boolean active,
            Instant createdAt) {

        static PatientResponse from(Patient patient, Clock clock) {
            return new PatientResponse(patient.id(), patient.cns().masked(),
                    patient.cpf() == null ? null : patient.cpf().masked(), patient.fullName(), patient.socialName(),
                    patient.birthDate(), patient.municipalityCode().value(), patient.phone().value(),
                    patient.preferredChannel(), patient.whatsappOptIn(), patient.pregnant(), patient.disability(),
                    patient.isPriorityGroup(clock), patient.isActive(), patient.createdAt());
        }
    }
}
