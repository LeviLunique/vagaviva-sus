package br.com.vagaviva.engagement.adapter.in.web;

import static br.com.vagaviva.engagement.fixtures.EngagementFixture.CLOCK;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases;
import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases.NotificationFilter;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.ActionResult;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.PatientAction;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.PatientAppointmentView;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationType;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = {PatientActionController.class, ShortLinkController.class, NotificationController.class,
        SandboxInboxController.class}, properties = "vagaviva.demo.enabled=true")
@Import(WebSliceSecurity.class)
class EngagementControllersTest {

    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUv";

    @Autowired MockMvcTester mvc;
    @MockitoBean PatientActionUseCases actions;
    @MockitoBean NotificationQueryUseCases queries;

    @Test
    @DisplayName("RF-26: link do paciente sem login — ver e agir; o IP vai para a auditoria")
    void shouldServePatientLinkWithoutLogin() {
        when(actions.view(eq(TOKEN), any())).thenReturn(new PatientAppointmentView("CONSULTATION", "Maria", "consulta",
                NOW, "AME Zona Norte", "Av. Norte, 1500", AppointmentStatus.PENDING_CONFIRMATION, NOW,
                List.of(PatientAction.CONFIRM, PatientAction.CANCEL)));
        when(actions.confirm(eq(TOKEN), any())).thenReturn(new ActionResult(AppointmentStatus.CONFIRMED, false));
        when(actions.cancel(eq(TOKEN), any())).thenReturn(new ActionResult(AppointmentStatus.CANCELLED_BY_PATIENT, true));
        when(actions.withdraw(eq(TOKEN), any())).thenReturn(new ActionResult(AppointmentStatus.WITHDRAWN, false));

        var view = mvc.get().uri("/api/v1/patient-actions/{t}", TOKEN).exchange();
        assertThat(view).hasStatusOk();
        assertThat(view).bodyJson().extractingPath("$.firstName").isEqualTo("Maria");
        assertThat(view).bodyJson().extractingPath("$.allowedActions[0]").isEqualTo("CONFIRM");
        assertThat(mvc.post().uri("/api/v1/patient-actions/{t}/confirm", TOKEN)).bodyJson()
                .extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(mvc.post().uri("/api/v1/patient-actions/{t}/cancel", TOKEN)).bodyJson()
                .extractingPath("$.backToQueue").isEqualTo(true);
        assertThat(mvc.post().uri("/api/v1/patient-actions/{t}/withdraw", TOKEN)).bodyJson()
                .extractingPath("$.status").isEqualTo("WITHDRAWN");
        verify(actions).view(TOKEN, "127.0.0.1");
    }

    @Test
    @DisplayName("RF-26 CA1: link inválido ⇒ 404; expirado ⇒ 410 (problem+json)")
    void shouldMapLinkErrors() {
        when(actions.view(eq("x"), any())).thenThrow(new NotFoundException("PATIENT_LINK_NOT_FOUND", "Link inválido."));
        when(actions.confirm(eq(TOKEN), any())).thenThrow(new GoneException("PATIENT_LINK_EXPIRED", "Link expirado."));

        assertThat(mvc.get().uri("/api/v1/patient-actions/x")).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.code").isEqualTo("PATIENT_LINK_NOT_FOUND");
        assertThat(mvc.post().uri("/api/v1/patient-actions/{t}/confirm", TOKEN)).hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("PATIENT_LINK_EXPIRED");
    }

    @Test
    @DisplayName("link curto /p/{token} ⇒ 302 para o recurso do agendamento, sem login")
    void shouldRedirectShortLink() {
        assertThat(mvc.get().uri("/p/{t}", TOKEN)).hasStatus(HttpStatus.FOUND)
                .hasHeader("Location", "/api/v1/patient-actions/" + TOKEN);
    }

    @Test
    @DisplayName("RF-28: log sem texto nem telefone; SCHEDULER fora do escopo ⇒ 403; REQUESTER ⇒ 403; sem token ⇒ 401")
    void shouldListNotificationLog() throws Exception {
        UUID appointment = UUID.randomUUID();
        Notification notification = Notification.create(UUID.randomUUID(), appointment, null, null,
                NotificationType.APPOINTMENT_SCHEDULED, NotificationChannel.SANDBOX, "a".repeat(64), "texto secreto", CLOCK);
        when(queries.list(eq(new NotificationFilter(appointment, null)), any(), any()))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(queries.list(eq(new NotificationFilter(null, null)), any(), any()))
                .thenThrow(new ForbiddenOperationException("NOTIFICATIONS_OUT_OF_UNIT", "Fora da unidade."));

        var page = mvc.get().uri("/api/v1/notifications?appointmentId={a}", appointment)
                .with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UUID.randomUUID())).exchange();
        assertThat(page).hasStatusOk();
        assertThat(page).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING");
        assertThat(page).bodyJson().extractingPath("$.content[0].channel").isEqualTo("SANDBOX");
        assertThat(page.getResponse().getContentAsString()).doesNotContain("texto secreto").doesNotContain("destination");
        assertThat(mvc.get().uri("/api/v1/notifications").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UUID.randomUUID())))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/notifications").with(TestJwt.as(Role.REQUESTER))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/notifications")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("RF-29: caixa sandbox (ADMIN) mostra o texto com o link; SCHEDULER ⇒ 403")
    void shouldShowSandboxInbox() {
        UUID patient = UUID.randomUUID();
        Notification notification = Notification.create(patient, UUID.randomUUID(), null, null,
                NotificationType.APPOINTMENT_SCHEDULED, NotificationChannel.SANDBOX, "a".repeat(64),
                "Confirme: https://vagaviva.test/p/" + TOKEN, CLOCK);
        when(queries.sandboxInbox(patient, 5)).thenReturn(List.of(notification));

        assertThat(mvc.get().uri("/api/v1/dev/sandbox/messages?patientId={p}&limit=5", patient).with(TestJwt.as(Role.ADMIN)))
                .hasStatusOk().bodyJson().extractingPath("$[0].body").asString().contains("/p/" + TOKEN);
        assertThat(mvc.get().uri("/api/v1/dev/sandbox/messages?patientId={p}", patient)
                .with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UUID.randomUUID()))).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(actions);
    }
}
