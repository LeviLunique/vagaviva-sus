package br.com.vagaviva.patient.application.service;

import static br.com.vagaviva.patient.fixtures.PatientFixture.CLOCK;
import static br.com.vagaviva.patient.fixtures.PatientFixture.VALID_CNS;
import static br.com.vagaviva.patient.fixtures.PatientFixture.VALID_CPF;
import static br.com.vagaviva.patient.fixtures.PatientFixture.aPatient;
import static br.com.vagaviva.patient.fixtures.PatientFixture.aPatientData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.application.port.in.ReadPatientUseCase.SearchPatientQuery;
import br.com.vagaviva.patient.application.port.in.RegisterPatientUseCase.RegisterPatientCommand;
import br.com.vagaviva.patient.application.port.in.UpdatePatientContactUseCase.UpdatePatientContactCommand;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientServicesTest {

    private static final CurrentUser REQUESTER = new CurrentUser(UUID.randomUUID(), Role.REQUESTER, UUID.randomUUID(), "Rita");
    private static final String IP = "10.0.0.7";

    @Mock PatientRepository repository;
    @Mock PatientAudit audit;

    private RegisterPatientCommand command(String cns, String cpf, String phone) {
        return new RegisterPatientCommand(cns, cpf, "Maria da Silva", null, LocalDate.of(1950, 1, 1), "3550308", phone,
                ContactChannel.SMS, false, false, false, REQUESTER, IP);
    }

    @Test
    @DisplayName("RF-08: cadastra paciente (idoso ⇒ grupo prioritário) e audita PATIENT_CREATED")
    void shouldRegisterPatient() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new RegisterPatientService(repository, audit, CLOCK);

        Patient patient = service.register(command(VALID_CNS, VALID_CPF, "+5511999990001"));

        assertThat(patient.cns().value()).isEqualTo(VALID_CNS);
        assertThat(patient.isPriorityGroup(CLOCK)).isTrue();
        verify(audit).record(REQUESTER, PatientAudit.PATIENT_CREATED, patient.id(), AuditOutcome.SUCCESS, IP, Map.of());
    }

    @Test
    @DisplayName("CPF é opcional: em branco é tratado como ausente")
    void shouldAcceptMissingCpf() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new RegisterPatientService(repository, audit, CLOCK);

        assertThat(service.register(command(VALID_CNS, " ", "+5511999990001")).cpf()).isNull();
        assertThat(service.register(command(VALID_CNS, null, "+5511999990001")).cpf()).isNull();
        verify(repository, never()).existsByCpf(any());
    }

    @Test
    @DisplayName("RF-08 CA1: CNS ou CPF duplicado ⇒ 409")
    void shouldRejectDuplicates() {
        var service = new RegisterPatientService(repository, audit, CLOCK);
        when(repository.existsByCns(Cns.of(VALID_CNS))).thenReturn(true, false);
        when(repository.existsByCpf(Cpf.of(VALID_CPF))).thenReturn(true);

        assertThatThrownBy(() -> service.register(command(VALID_CNS, VALID_CPF, "+5511999990001")))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CNS_ALREADY_REGISTERED");
        assertThatThrownBy(() -> service.register(command(VALID_CNS, VALID_CPF, "+5511999990001")))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CPF_ALREADY_REGISTERED");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("documentos e telefone inválidos ⇒ 422 antes de qualquer acesso ao banco")
    void shouldRejectInvalidDocuments() {
        var service = new RegisterPatientService(repository, audit, CLOCK);

        assertThatThrownBy(() -> service.register(command("115881399860001", null, "+5511999990001")))
                .extracting("code").isEqualTo("INVALID_CNS");
        assertThatThrownBy(() -> service.register(command(VALID_CNS, "11111111111", "+5511999990001")))
                .extracting("code").isEqualTo("INVALID_CPF");
        assertThatThrownBy(() -> service.register(command(VALID_CNS, null, "1199999")))
                .extracting("code").isEqualTo("INVALID_PHONE");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("RF-05: leitura por id é auditada como PATIENT_READ; inexistente ⇒ 404")
    void shouldAuditReadById() {
        var service = new ReadPatientService(repository, audit);
        Patient patient = aPatient();
        when(repository.findById(patient.id())).thenReturn(Optional.of(patient));

        assertThat(service.get(patient.id(), REQUESTER, IP)).isSameAs(patient);
        verify(audit).record(REQUESTER, PatientAudit.PATIENT_READ, patient.id(), AuditOutcome.SUCCESS, IP, Map.of("via", "ID"));
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), REQUESTER, IP)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("RF-10: busca por CNS ou por CPF é auditada sem registrar o documento pesquisado")
    void shouldSearchByDocument() {
        var service = new ReadPatientService(repository, audit);
        Patient patient = aPatient();
        when(repository.findByCns(Cns.of(VALID_CNS))).thenReturn(Optional.of(patient));
        when(repository.findByCpf(Cpf.of(VALID_CPF))).thenReturn(Optional.of(patient));

        assertThat(service.search(new SearchPatientQuery(VALID_CNS, null), REQUESTER, IP)).isSameAs(patient);
        assertThat(service.search(new SearchPatientQuery("", VALID_CPF), REQUESTER, IP)).isSameAs(patient);

        verify(audit).record(REQUESTER, PatientAudit.PATIENT_READ, patient.id(), AuditOutcome.SUCCESS, IP,
                Map.of("via", "SEARCH_CNS"));
        verify(audit).record(REQUESTER, PatientAudit.PATIENT_READ, patient.id(), AuditOutcome.SUCCESS, IP,
                Map.of("via", "SEARCH_CPF"));
    }

    @Test
    @DisplayName("busca sem resultado ⇒ 404 e fica na trilha como PATIENT_SEARCHED/FAILURE")
    void shouldAuditMissedSearch() {
        var service = new ReadPatientService(repository, audit);
        when(repository.findByCns(Cns.of(VALID_CNS))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(new SearchPatientQuery(VALID_CNS, null), REQUESTER, IP))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("PATIENT_NOT_FOUND");
        verify(audit).record(eq(REQUESTER), eq(PatientAudit.PATIENT_SEARCHED), isNull(), eq(AuditOutcome.FAILURE),
                eq(IP), eq(Map.of("via", "CNS")));
    }

    @Test
    @DisplayName("busca com nenhum ou com os dois critérios ⇒ INVALID_SEARCH_CRITERIA (422)")
    void shouldRequireExactlyOneCriterion() {
        var service = new ReadPatientService(repository, audit);

        assertThatThrownBy(() -> service.search(new SearchPatientQuery(null, " "), REQUESTER, IP))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_SEARCH_CRITERIA");
        assertThatThrownBy(() -> service.search(new SearchPatientQuery(VALID_CNS, VALID_CPF), REQUESTER, IP))
                .extracting("code").isEqualTo("INVALID_SEARCH_CRITERIA");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("RF-09: atualiza contato e grava só quando algo mudou; auditoria indica a mudança")
    void shouldUpdateContact() {
        var service = new UpdatePatientContactService(repository, audit, CLOCK);
        Patient patient = aPatient();
        when(repository.findById(patient.id())).thenReturn(Optional.of(patient));
        when(repository.save(patient)).thenReturn(patient);

        Patient updated = service.update(new UpdatePatientContactCommand(patient.id(), "+5511988887777", ContactChannel.SMS,
                false, REQUESTER, IP));
        service.update(new UpdatePatientContactCommand(patient.id(), null, null, null, REQUESTER, IP));

        assertThat(updated.phone().value()).isEqualTo("+5511988887777");
        verify(repository).save(patient);
        verify(audit).record(REQUESTER, PatientAudit.PATIENT_CONTACT_UPDATED, patient.id(), AuditOutcome.SUCCESS, IP,
                Map.of("changed", "true"));
        verify(audit).record(REQUESTER, PatientAudit.PATIENT_CONTACT_UPDATED, patient.id(), AuditOutcome.SUCCESS, IP,
                Map.of("changed", "false"));
    }

    @Test
    @DisplayName("atualizar contato de paciente inexistente ⇒ 404; telefone inválido ⇒ 422")
    void shouldValidateContactUpdate() {
        var service = new UpdatePatientContactService(repository, audit, CLOCK);
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdatePatientContactCommand(unknown, null, ContactChannel.SMS, null,
                REQUESTER, IP))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(new UpdatePatientContactCommand(unknown, "123", null, null, REQUESTER, IP)))
                .extracting("code").isEqualTo("INVALID_PHONE");
    }

    @Test
    @DisplayName("PatientApi devolve resumo sem documentos, com primeiro nome (social) e grupo prioritário")
    void shouldExposeSummary() {
        var api = new PatientApiImpl(repository, CLOCK);
        Patient patient = Patient.register(aPatientData().socialName("Joana").pregnant(true), CLOCK);
        when(repository.findById(patient.id())).thenReturn(Optional.of(patient));

        assertThat(api.findSummary(patient.id())).hasValueSatisfying(summary -> {
            assertThat(summary.firstName()).isEqualTo("Joana");
            assertThat(summary.priorityGroup()).isTrue();
            assertThat(summary.phone()).isEqualTo("+5511999990001");
            assertThat(summary.preferredChannel()).isEqualTo(ContactChannel.WHATSAPP);
        });
        assertThat(api.findSummary(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("PatientApi em lote: um acesso ao repositório, CNS só mascarado; lista vazia não consulta")
    void shouldExposeSummariesInBatch() {
        var api = new PatientApiImpl(repository, CLOCK);
        Patient patient = aPatient();
        when(repository.findAllById(java.util.List.of(patient.id()))).thenReturn(java.util.List.of(patient));

        assertThat(api.findSummaries(java.util.List.of(patient.id())))
                .containsOnlyKeys(patient.id())
                .extractingByKey(patient.id())
                .satisfies(summary -> assertThat(summary.cnsMasked()).isEqualTo("***********0000"));
        assertThat(api.findSummaries(java.util.List.of())).isEmpty();
    }
}
