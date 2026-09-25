package br.com.vagaviva.patient.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.patient.application.port.in.RegisterPatientUseCase;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegisterPatientService implements RegisterPatientUseCase {

    private final PatientRepository repository;
    private final PatientAudit audit;
    private final Clock clock;

    RegisterPatientService(PatientRepository repository, PatientAudit audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Patient register(RegisterPatientCommand command) {
        Cns cns = Cns.of(command.cns());
        Cpf cpf = command.cpf() == null || command.cpf().isBlank() ? null : Cpf.of(command.cpf());
        Patient.Builder data = Patient.builder()
                .cns(cns)
                .cpf(cpf)
                .fullName(command.fullName())
                .socialName(command.socialName())
                .birthDate(command.birthDate())
                .municipalityCode(MunicipalityCode.of(command.municipalityCode()))
                .phone(PhoneNumber.of(command.phone()))
                .preferredChannel(command.preferredChannel())
                .whatsappOptIn(command.whatsappOptIn())
                .pregnant(command.pregnant())
                .disability(command.disability());
        if (repository.existsByCns(cns)) {
            throw PatientErrors.cnsAlreadyRegistered();
        }
        if (cpf != null && repository.existsByCpf(cpf)) {
            throw PatientErrors.cpfAlreadyRegistered();
        }
        Patient patient = repository.save(Patient.register(data, clock));
        audit.record(command.actor(), PatientAudit.PATIENT_CREATED, patient.id(), AuditOutcome.SUCCESS,
                command.clientIp(), Map.of());
        return patient;
    }
}
