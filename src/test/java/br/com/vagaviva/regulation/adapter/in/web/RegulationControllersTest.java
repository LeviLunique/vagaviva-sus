package br.com.vagaviva.regulation.adapter.in.web;

import static br.com.vagaviva.regulation.fixtures.ReferralFixture.NOW;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aPendingReferral;
import static br.com.vagaviva.regulation.fixtures.ReferralFixture.aWaitingReferral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase;
import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase.PublicQueuePosition;
import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase.PublicSpecialtyStats;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.QueueItem;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.ReferralFilter;
import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase;
import br.com.vagaviva.regulation.application.port.in.QueueSnapshotUseCase.SnapshotResult;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.RegulationDecision;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({ReferralController.class, QueueController.class, PublicTransparencyController.class})
@Import(WebSliceSecurity.class)
class RegulationControllersTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ReferralCommandUseCases commands;
    @MockitoBean QueryReferralsUseCase queries;
    @MockitoBean QueueSnapshotUseCase snapshot;
    @MockitoBean PublicTransparencyUseCase transparency;

    private static String createBody(UUID patient, UUID specialty) {
        return """
                {"patientId":"%s","specialtyId":"%s","clinicalJustification":"Dor torácica.","cid10":"I20"}"""
                .formatted(patient, specialty);
    }

    @Test
    @DisplayName("REQUESTER registra encaminhamento ⇒ 201 com protocolo; aceita-encaixe ausente = false")
    void shouldCreateReferral() {
        Referral referral = aPendingReferral();
        when(commands.create(any())).thenReturn(referral);

        var result = mvc.post().uri("/api/v1/referrals").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID(), UUID.randomUUID()))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/v1/referrals/" + referral.id());
        assertThat(result).bodyJson().extractingPath("$.protocol").isEqualTo("VV-2026-0000123");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING_REGULATION");
        verify(commands).create(argThat(command -> !command.acceptsShortNotice()));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"REGULATOR", "SCHEDULER", "MANAGER", "ADMIN"})
    @DisplayName("só REQUESTER encaminha")
    void shouldForbidOtherRolesToCreate(Role role) {
        assertThat(mvc.post().uri("/api/v1/referrals").with(TestJwt.as(role)).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(UUID.randomUUID(), UUID.randomUUID()))).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(commands);
    }

    @Test
    @DisplayName("encaminhamento sem justificativa ⇒ 422; duplicado ⇒ 409")
    void shouldValidateAndMapConflict() {
        assertThat(mvc.post().uri("/api/v1/referrals").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":\"%s\"}".formatted(UUID.randomUUID())))
                .hasStatus(422);
        when(commands.create(any())).thenThrow(new ConflictException("REFERRAL_DUPLICATED", "duplicado"));
        assertThat(mvc.post().uri("/api/v1/referrals").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID(), UUID.randomUUID())))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("REFERRAL_DUPLICATED");
    }

    @Test
    @DisplayName("REGULATOR aprova com risco; REQUESTER tentando regular ⇒ 403")
    void shouldRegulateOnlyAsRegulator() {
        Referral waiting = aWaitingReferral();
        when(commands.regulate(any())).thenReturn(waiting);
        String body = "{\"decision\":\"APPROVE\",\"riskClass\":\"YELLOW\"}";

        assertThat(mvc.post().uri("/api/v1/referrals/{id}/regulation", waiting.id()).with(TestJwt.as(Role.REGULATOR))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("WAITING");
        verify(commands).regulate(argThat(command -> command.decision() == RegulationDecision.APPROVE
                && command.riskClass() == RiskClass.YELLOW));
        assertThat(mvc.post().uri("/api/v1/referrals/{id}/regulation", waiting.id()).with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content(body)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("consulta, listagem, reenvio e cancelamento pelos papéis permitidos")
    void shouldServeReferralQueriesAndCommands() {
        Referral referral = aPendingReferral();
        when(queries.get(eq(referral.id()), any(), any())).thenReturn(referral);
        when(queries.list(eq(new ReferralFilter(ReferralStatus.WAITING, null, null)), eq(PageRequest.of(0, 20)), any()))
                .thenReturn(new PageImpl<>(List.of(referral)));
        when(commands.resubmit(any())).thenReturn(referral);
        when(commands.cancel(eq(referral.id()), eq("Duplicidade"), any(), any())).thenReturn(referral);

        assertThat(mvc.get().uri("/api/v1/referrals/{id}", referral.id()).with(TestJwt.as(Role.REQUESTER)))
                .hasStatusOk().bodyJson().extractingPath("$.clinicalJustification").isNotNull();
        var list = mvc.get().uri("/api/v1/referrals?status=WAITING").with(TestJwt.as(Role.REGULATOR)).exchange();
        assertThat(list).hasStatusOk();
        assertThat(list).bodyText().doesNotContain("clinicalJustification");
        assertThat(mvc.put().uri("/api/v1/referrals/{id}", referral.id()).with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clinicalJustification\":\"Corrigido\"}")).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/referrals/{id}/cancel", referral.id()).with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Duplicidade\"}")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/referrals").with(TestJwt.as(Role.MANAGER))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("fila: REGULATOR/MANAGER leem; REQUESTER ⇒ 403; snapshot sob demanda só ADMIN")
    void shouldServeQueue() {
        UUID specialty = UUID.randomUUID();
        var item = new QueueItem(1, UUID.randomUUID(), "VV-2026-0000001", RiskClass.RED, true, NOW, 3, UUID.randomUUID(),
                "Maria", "***********1234");
        when(queries.queue(eq(specialty), eq(PageRequest.of(0, 20)), any(), any())).thenReturn(new PageImpl<>(List.of(item)));
        when(snapshot.refresh()).thenReturn(new SnapshotResult(NOW, 12));

        assertThat(mvc.get().uri("/api/v1/queues/{id}", specialty).with(TestJwt.as(Role.MANAGER)))
                .hasStatusOk().bodyJson().extractingPath("$.content[0].patientCnsMasked").isEqualTo("***********1234");
        assertThat(mvc.get().uri("/api/v1/queues/{id}", specialty).with(TestJwt.as(Role.REQUESTER)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/v1/queues/snapshot").with(TestJwt.as(Role.ADMIN)))
                .hasStatusOk().bodyJson().extractingPath("$.queued").isEqualTo(12);
        assertThat(mvc.post().uri("/api/v1/queues/snapshot").with(TestJwt.as(Role.REGULATOR)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RF-17/18: rotas públicas sem token; dados no corpo; 404 genérico")
    void shouldServePublicRoutesWithoutToken() {
        when(transparency.position(eq("VV-2026-0000123"), eq(LocalDate.of(1950, 1, 1)), any()))
                .thenReturn(new PublicQueuePosition("VV-2026-0000123", ReferralStatus.WAITING, "Cardiologia",
                        RiskClass.YELLOW, 7, 40, 3, NOW));
        when(transparency.position(eq("VV-2026-0000999"), any(), any()))
                .thenThrow(new NotFoundException("QUEUE_POSITION_NOT_FOUND", "não encontrado"));
        when(transparency.stats()).thenReturn(List.of(new PublicSpecialtyStats(UUID.randomUUID(), "Cardiologia",
                Map.of(RiskClass.RED, 1), 1, null, null, NOW)));

        assertThat(mvc.post().uri("/api/v1/public/queue-position").contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocol\":\"VV-2026-0000123\",\"birthDate\":\"1950-01-01\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.position").isEqualTo(7);
        assertThat(mvc.post().uri("/api/v1/public/queue-position").contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocol\":\"VV-2026-0000999\",\"birthDate\":\"1950-01-01\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/v1/public/queue-position").contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocol\":\"VV-2026-0000123\"}")).hasStatus(422);
        assertThat(mvc.get().uri("/api/v1/public/queue-stats"))
                .hasStatusOk().bodyJson().extractingPath("$[0].waitingByRisk.RED").isEqualTo(1);
    }
}
