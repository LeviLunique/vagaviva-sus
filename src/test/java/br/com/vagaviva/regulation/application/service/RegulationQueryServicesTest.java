package br.com.vagaviva.regulation.application.service;

import static br.com.vagaviva.regulation.fixtures.ReferralFixture.CLOCK;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.NOW;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.UNIT;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aPendingReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aWaitingReferral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.QueueItem;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.ReferralFilter;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository.QueuePositionSnapshot;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository.SpecialtyQueueStats;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RegulationQueryServicesTest {

    private static final CurrentUser REQUESTER = new CurrentUser(UUID.randomUUID(), Role.REQUESTER, UNIT, "Rita");
    private static final CurrentUser REGULATOR = new CurrentUser(UUID.randomUUID(), Role.REGULATOR, null, "Renato");
    private static final LocalDate BIRTH = LocalDate.of(1950, 1, 1);

    @Mock ReferralRepository repository;
    @Mock QueueSnapshotRepository snapshots;
    @Mock PatientApi patients;
    @Mock CatalogApi catalog;
    @Mock RegulationAudit audit;

    private ReferralQueryService queries() {
        return new ReferralQueryService(repository, patients, catalog, audit, CLOCK);
    }

    private PublicTransparencyService transparency() {
        return new PublicTransparencyService(repository, snapshots, patients, catalog, audit);
    }

    private static PatientSummary patient(UUID id) {
        return new PatientSummary(id, "Maria", "***********1234", BIRTH, "3550308", "+5511999990001",
                ContactChannel.SMS, false, false, true);
    }

    private static SpecialtySummary specialty(UUID id, boolean sensitive, SpecialtyType type) {
        return new SpecialtySummary(id, "PSIQ", "Psiquiatria", type, sensitive, true);
    }

    @Test
    @DisplayName("leitura auditada; REQUESTER de outra unidade ⇒ REFERRAL_OUT_OF_UNIT; regulador lê qualquer uma")
    void shouldGetWithUnitScope() {
        Referral referral = aPendingReferral();
        when(repository.findById(referral.id())).thenReturn(Optional.of(referral));
        var otherUnit = new CurrentUser(UUID.randomUUID(), Role.REQUESTER, UUID.randomUUID(), "Outra");

        assertThat(queries().get(referral.id(), REQUESTER, "ip")).isSameAs(referral);
        assertThat(queries().get(referral.id(), REGULATOR, "ip")).isSameAs(referral);
        assertThatThrownBy(() -> queries().get(referral.id(), otherUnit, "ip")).isInstanceOf(ForbiddenOperationException.class);
        verify(audit).record(REQUESTER, RegulationAudit.REFERRAL_READ, RegulationAudit.REFERRAL, referral.id(),
                AuditOutcome.SUCCESS, "ip", Map.of());
    }

    @Test
    @DisplayName("RN-03: na listagem, REQUESTER vê só a própria unidade; regulador usa o filtro informado")
    void shouldScopeListByUnit() {
        UUID specialty = UUID.randomUUID();
        var filter = new ReferralFilter(ReferralStatus.WAITING, specialty, null);
        when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        queries().list(filter, PageRequest.of(0, 20), REQUESTER);
        queries().list(filter, PageRequest.of(0, 20), REGULATOR);

        verify(repository).search(new ReferralFilter(ReferralStatus.WAITING, specialty, UNIT), PageRequest.of(0, 20));
        verify(repository).search(filter, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("RF-16: fila com posição real (offset da página), dias de espera e paciente mascarado em lote")
    void shouldBuildQueuePage() {
        UUID specialty = UUID.randomUUID();
        Referral first = aWaitingReferral();
        Referral second = aWaitingReferral();
        when(catalog.findSpecialty(specialty)).thenReturn(Optional.of(specialty(specialty, false, SpecialtyType.CONSULTATION)));
        when(repository.findQueue(specialty, PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 10));
        when(patients.findSummaries(List.of(first.patientId(), second.patientId())))
                .thenReturn(Map.of(first.patientId(), patient(first.patientId())));
        var later = java.time.Clock.fixed(NOW.plus(Duration.ofDays(12)), CLOCK.getZone());

        var page = new ReferralQueryService(repository, patients, catalog, audit, later)
                .queue(specialty, PageRequest.of(1, 2), REGULATOR, null);

        assertThat(page.getContent()).extracting(QueueItem::position).containsExactly(3, 4);
        QueueItem item = page.getContent().getFirst();
        assertThat(item.waitingDays()).isEqualTo(12);
        assertThat(item.patientFirstName()).isEqualTo("Maria");
        assertThat(item.patientCnsMasked()).isEqualTo("***********1234");
        assertThat(page.getContent().get(1).patientFirstName()).isNull();
        verify(audit).record(REGULATOR, RegulationAudit.QUEUE_READ, RegulationAudit.QUEUE, specialty,
                AuditOutcome.SUCCESS, null, Map.of("page", "1"));
    }

    @Test
    @DisplayName("fila de especialidade inexistente ⇒ SPECIALTY_NOT_FOUND")
    void shouldRejectUnknownSpecialtyQueue() {
        assertThatThrownBy(() -> queries().queue(UUID.randomUUID(), PageRequest.of(0, 20), REGULATOR, null))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("SPECIALTY_NOT_FOUND");
    }

    @Test
    @DisplayName("RF-17: protocolo + nascimento conferem ⇒ posição do snapshot e espera estimada (RN-08)")
    void shouldReturnPublicPosition() {
        Referral referral = aWaitingReferral();
        when(repository.findByProtocol("VV-2026-0000123")).thenReturn(Optional.of(referral));
        when(patients.findSummary(referral.patientId())).thenReturn(Optional.of(patient(referral.patientId())));
        when(catalog.findSpecialty(referral.specialtyId()))
                .thenReturn(Optional.of(new SpecialtySummary(referral.specialtyId(), "CARDIO", "Cardiologia",
                        SpecialtyType.CONSULTATION, false, true)));
        when(snapshots.findPosition(referral.id())).thenReturn(Optional.of(
                new QueuePositionSnapshot(referral.id(), referral.specialtyId(), 7, 40, RiskClass.YELLOW, NOW)));
        when(snapshots.findStats(referral.specialtyId())).thenReturn(Optional.of(new SpecialtyQueueStats(
                referral.specialtyId(), 1, 2, 3, 4, BigDecimal.TEN, new BigDecimal("2.50"), NOW)));

        var position = transparency().position(" vv-2026-0000123 ", BIRTH, "ip");

        assertThat(position.position()).isEqualTo(7);
        assertThat(position.totalInQueue()).isEqualTo(40);
        assertThat(position.estimatedWaitDays()).isEqualTo(3);
        assertThat(position.specialty()).isEqualTo("Cardiologia");
        verify(audit).record(null, RegulationAudit.PUBLIC_POSITION_READ, RegulationAudit.REFERRAL, referral.id(),
                AuditOutcome.SUCCESS, "ip", Map.of());
    }

    @Test
    @DisplayName("RF-17: data divergente, protocolo inexistente ou malformado ⇒ o mesmo 404 genérico, auditado sem recurso")
    void shouldHideMismatches() {
        Referral referral = aWaitingReferral();
        when(repository.findByProtocol("VV-2026-0000123")).thenReturn(Optional.of(referral));
        when(patients.findSummary(referral.patientId())).thenReturn(Optional.of(patient(referral.patientId())));

        for (String protocol : new String[] {"VV-2026-0000123", "VV-2026-9999999", "abc"}) {
            LocalDate birth = protocol.equals("VV-2026-0000123") ? BIRTH.plusDays(1) : BIRTH;
            assertThatThrownBy(() -> transparency().position(protocol, birth, "ip"))
                    .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("QUEUE_POSITION_NOT_FOUND");
        }
        verify(audit, org.mockito.Mockito.times(3)).record(isNull(), eq(RegulationAudit.PUBLIC_POSITION_READ), any(),
                isNull(), eq(AuditOutcome.FAILURE), eq("ip"), eq(Map.of()));
    }

    @Test
    @DisplayName("fora da fila (ou sem snapshot ainda) ⇒ só status; especialidade sensível vira rótulo genérico (RN-20)")
    void shouldAnswerStatusOnlyOutsideQueue() {
        Referral pending = aPendingReferral();
        when(repository.findByProtocol(pending.protocol().value())).thenReturn(Optional.of(pending));
        when(patients.findSummary(pending.patientId())).thenReturn(Optional.of(patient(pending.patientId())));
        when(catalog.findSpecialty(pending.specialtyId()))
                .thenReturn(Optional.of(specialty(pending.specialtyId(), true, SpecialtyType.CONSULTATION)));

        var position = transparency().position(pending.protocol().value(), BIRTH, null);

        assertThat(position.status()).isEqualTo(ReferralStatus.PENDING_REGULATION);
        assertThat(position.position()).isNull();
        assertThat(position.snapshotAt()).isNull();
        assertThat(position.specialty()).isEqualTo("Consulta especializada");
    }

    @Test
    @DisplayName("RN-08: sem vazão ⇒ espera indisponível; rótulo genérico de exame sensível")
    void shouldEstimateWaitAndLabel() {
        assertThat(PublicTransparencyService.estimatedWaitDays(10, null)).isNull();
        assertThat(PublicTransparencyService.estimatedWaitDays(10, BigDecimal.ZERO)).isNull();
        assertThat(PublicTransparencyService.estimatedWaitDays(10, new BigDecimal("3"))).isEqualTo(4);
        assertThat(PublicTransparencyService.label(specialty(UUID.randomUUID(), true, SpecialtyType.EXAM)))
                .isEqualTo("Exame especializado");
    }

    @Test
    @DisplayName("RF-18: estatísticas por especialidade, ordenadas por nome, ignorando especialidade removida")
    void shouldListPublicStats() {
        UUID cardio = UUID.randomUUID();
        UUID ortop = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(snapshots.findAllStats()).thenReturn(List.of(
                new SpecialtyQueueStats(ortop, 0, 1, 0, 0, null, null, NOW),
                new SpecialtyQueueStats(cardio, 2, 0, 1, 1, BigDecimal.ONE, BigDecimal.TEN, NOW),
                new SpecialtyQueueStats(gone, 0, 0, 0, 0, null, null, NOW)));
        when(catalog.findSpecialty(cardio)).thenReturn(Optional.of(
                new SpecialtySummary(cardio, "CARDIO", "Cardiologia", SpecialtyType.CONSULTATION, false, true)));
        when(catalog.findSpecialty(ortop)).thenReturn(Optional.of(
                new SpecialtySummary(ortop, "ORTOP", "Ortopedia", SpecialtyType.CONSULTATION, false, true)));
        when(catalog.findSpecialty(gone)).thenReturn(Optional.empty());

        var stats = transparency().stats();

        assertThat(stats).extracting(s -> s.specialty()).containsExactly("Cardiologia", "Ortopedia");
        assertThat(stats.getFirst().totalWaiting()).isEqualTo(4);
        assertThat(stats.getFirst().waitingByRisk()).containsEntry(RiskClass.RED, 2).containsEntry(RiskClass.BLUE, 1);
    }
}
