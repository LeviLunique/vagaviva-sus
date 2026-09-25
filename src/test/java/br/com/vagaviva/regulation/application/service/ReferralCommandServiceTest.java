package br.com.vagaviva.regulation.application.service;

import static br.com.vagaviva.regulation.fixtures.ReferralFixture.CLOCK;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.UNIT;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aPendingReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aReturnedReferral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.CreateReferralCommand;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.RegulateReferralCommand;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.RegulationDecision;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.ResubmitReferralCommand;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReferralCommandServiceTest {

    private static final CurrentUser REQUESTER = new CurrentUser(UUID.randomUUID(), Role.REQUESTER, UNIT, "Rita");
    private static final CurrentUser REGULATOR = new CurrentUser(UUID.randomUUID(), Role.REGULATOR, null, "Renato");
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID SPECIALTY = UUID.randomUUID();

    @Mock ReferralRepository repository;
    @Mock ProtocolGenerator protocols;
    @Mock PatientApi patients;
    @Mock CatalogApi catalog;
    @Mock RegulationAudit audit;
    @Mock ApplicationEventPublisher events;

    private ReferralCommandService service;

    @BeforeEach
    void setUp() {
        service = new ReferralCommandService(repository, protocols, patients, catalog, audit, events, CLOCK);
        lenient().when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(protocols.next()).thenReturn(Protocol.of(2026, 42));
    }

    private static PatientSummary patient(boolean active, boolean priority) {
        return new PatientSummary(PATIENT, "Maria", "***********0000", LocalDate.of(1950, 1, 1), "3518800",
                "+5511999990001", ContactChannel.SMS, false, priority, active);
    }

    private void specialtyIs(boolean active) {
        when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.of(
                new SpecialtySummary(SPECIALTY, "CARDIO", "Cardiologia", SpecialtyType.CONSULTATION, false, active)));
    }

    private CreateReferralCommand create(CurrentUser actor) {
        return new CreateReferralCommand(PATIENT, SPECIALTY, "Dor torácica.", "I20", true, actor, "10.0.0.1");
    }

    @Test
    @DisplayName("RF-12: cria com protocolo, unidade do solicitante e município do paciente; audita REFERRAL_CREATED")
    void shouldCreateReferral() {
        when(patients.findSummary(PATIENT)).thenReturn(Optional.of(patient(true, false)));
        specialtyIs(true);

        Referral referral = service.create(create(REQUESTER));

        assertThat(referral.protocol().value()).isEqualTo("VV-2026-0000042");
        assertThat(referral.requesterUnitId()).isEqualTo(UNIT);
        assertThat(referral.requestedBy()).isEqualTo(REQUESTER.id());
        assertThat(referral.patientMunicipalityCode().value()).isEqualTo("3518800");
        verify(audit).record(REQUESTER, RegulationAudit.REFERRAL_CREATED, RegulationAudit.REFERRAL, referral.id(),
                AuditOutcome.SUCCESS, "10.0.0.1", Map.of("specialtyId", SPECIALTY.toString()));
    }

    @Test
    @DisplayName("RN-05: paciente já com encaminhamento ativo na especialidade ⇒ REFERRAL_DUPLICATED (409)")
    void shouldRejectDuplicate() {
        when(patients.findSummary(PATIENT)).thenReturn(Optional.of(patient(true, false)));
        specialtyIs(true);
        when(repository.existsActive(PATIENT, SPECIALTY)).thenReturn(true);

        assertThatThrownBy(() -> service.create(create(REQUESTER)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("REFERRAL_DUPLICATED");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("paciente inexistente/inativo ⇒ INVALID_PATIENT; especialidade inativa ⇒ INVALID_SPECIALTY (422)")
    void shouldValidatePatientAndSpecialty() {
        when(patients.findSummary(PATIENT)).thenReturn(Optional.empty(), Optional.of(patient(false, false)),
                Optional.of(patient(true, false)));
        assertThatThrownBy(() -> service.create(create(REQUESTER))).extracting("code").isEqualTo("INVALID_PATIENT");
        assertThatThrownBy(() -> service.create(create(REQUESTER))).extracting("code").isEqualTo("INVALID_PATIENT");

        specialtyIs(false);
        assertThatThrownBy(() -> service.create(create(REQUESTER)))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("INVALID_SPECIALTY");
    }

    @Test
    @DisplayName("usuário sem unidade não encaminha (403)")
    void shouldRequireRequesterUnit() {
        var withoutUnit = new CurrentUser(UUID.randomUUID(), Role.REQUESTER, null, "Sem unidade");

        assertThatThrownBy(() -> service.create(create(withoutUnit))).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("RF-13: aprovar calcula o grupo prioritário pela PatientApi, entra na fila e publica ReferralQueued")
    void shouldApproveAndPublishEvent() {
        Referral pending = aPendingReferral();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));
        when(patients.findSummary(pending.patientId())).thenReturn(Optional.of(patient(true, true)));

        Referral approved = service.regulate(new RegulateReferralCommand(pending.id(), RegulationDecision.APPROVE,
                RiskClass.RED, null, REGULATOR, null));

        assertThat(approved.status()).isEqualTo(ReferralStatus.WAITING);
        assertThat(approved.priorityGroup()).isTrue();
        verify(events).publishEvent(new ReferralQueued(pending.id(), pending.patientId(), pending.protocol().value()));
        verify(audit).record(REGULATOR, RegulationAudit.REFERRAL_REGULATED, RegulationAudit.REFERRAL, pending.id(),
                AuditOutcome.SUCCESS, null, Map.of("decision", "APPROVE", "riskClass", "RED"));
    }

    @Test
    @DisplayName("aprovar sem classe de risco ⇒ RISK_CLASS_REQUIRED; paciente sumido não é prioritário")
    void shouldRequireRiskClass() {
        Referral pending = aPendingReferral();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.regulate(new RegulateReferralCommand(pending.id(), RegulationDecision.APPROVE,
                null, null, REGULATOR, null))).extracting("code").isEqualTo("RISK_CLASS_REQUIRED");

        when(patients.findSummary(pending.patientId())).thenReturn(Optional.empty());
        assertThat(service.regulate(new RegulateReferralCommand(pending.id(), RegulationDecision.APPROVE,
                RiskClass.BLUE, null, REGULATOR, null)).priorityGroup()).isFalse();
    }

    @Test
    @DisplayName("RF-13: devolver registra a justificativa e não publica evento")
    void shouldReturnReferral() {
        Referral pending = aPendingReferral();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        Referral returned = service.regulate(new RegulateReferralCommand(pending.id(), RegulationDecision.RETURN,
                RiskClass.RED, "Anexar exames.", REGULATOR, null));

        assertThat(returned.status()).isEqualTo(ReferralStatus.RETURNED);
        verify(events, never()).publishEvent(any());
        verify(audit).record(eq(REGULATOR), eq(RegulationAudit.REFERRAL_REGULATED), any(), eq(pending.id()), any(),
                any(), eq(Map.of("decision", "RETURN")));
    }

    @Test
    @DisplayName("RF-14: reenvio pela unidade de origem; outra unidade ⇒ REFERRAL_OUT_OF_UNIT (403)")
    void shouldResubmitOnlyFromOwnUnit() {
        Referral returned = aReturnedReferral();
        when(repository.findById(returned.id())).thenReturn(Optional.of(returned));
        var otherUnit = new CurrentUser(UUID.randomUUID(), Role.REQUESTER, UUID.randomUUID(), "Outra");

        assertThatThrownBy(() -> service.resubmit(new ResubmitReferralCommand(returned.id(), "Corrigido", null, false,
                otherUnit, null))).isInstanceOf(ForbiddenOperationException.class)
                .extracting("code").isEqualTo("REFERRAL_OUT_OF_UNIT");

        Referral resubmitted = service.resubmit(new ResubmitReferralCommand(returned.id(), "Corrigido", null, false,
                REQUESTER, null));
        assertThat(resubmitted.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);
    }

    @Test
    @DisplayName("RF-15: cancela com motivo; inexistente ⇒ REFERRAL_NOT_FOUND")
    void shouldCancel() {
        Referral pending = aPendingReferral();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThat(service.cancel(pending.id(), "Duplicidade", REGULATOR, null).status()).isEqualTo(ReferralStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), "x", REGULATOR, null))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("REFERRAL_NOT_FOUND");
    }
}
