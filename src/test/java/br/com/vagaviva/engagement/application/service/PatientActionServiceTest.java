package br.com.vagaviva.engagement.application.service;

import static br.com.vagaviva.engagement.fixtures.EngagementFixture.CLOCK;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.NOW;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.SPECIALTY;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.UNIT;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.appointment;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.patient;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.specialty;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases.NotificationFilter;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.PatientAction;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.application.port.out.PatientActionTokenRepository;
import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.engagement.domain.TokenPurpose;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PatientActionServiceTest {

    @Mock PatientActionTokenRepository tokens;
    @Mock SchedulingApi scheduling;
    @Mock PatientApi patients;
    @Mock CatalogApi catalog;
    @Mock EngagementAudit audit;
    @Mock NotificationRepository notifications;

    private PatientActionService service;

    @BeforeEach
    void setUp() {
        service = new PatientActionService(tokens, scheduling, patients, catalog, audit, CLOCK);
        lenient().when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    /** Token válido para o agendamento; devolve o valor em claro. */
    private String tokenFor(AppointmentView view, Instant expiresAt, TokenPurpose purpose) {
        var issued = PatientActionToken.issue(purpose, view.id(), expiresAt, CLOCK);
        lenient().when(tokens.findByHash(issued.token().tokenHash())).thenReturn(Optional.of(issued.token()));
        return issued.raw();
    }

    @Test
    @DisplayName("ver pelo link: primeiro nome, rótulo genérico, unidade, prazo e ações permitidas agora")
    void shouldShowAppointmentWithAllowedActions() {
        AppointmentView view = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        String token = tokenFor(view, view.startAt(), TokenPurpose.APPOINTMENT);
        when(scheduling.findAppointmentView(view.id())).thenReturn(Optional.of(view));
        when(patients.findSummary(view.patientId())).thenReturn(Optional.of(patient(view.patientId(), false, true)));
        when(catalog.findUnit(UNIT)).thenReturn(Optional.of(unit()));
        when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.of(specialty(SpecialtyType.EXAM, true)));

        var shown = service.view(token, "10.0.0.9");

        assertThat(shown.firstName()).isEqualTo("Maria");
        assertThat(shown.label()).isEqualTo("exame");
        assertThat(shown.unitAddress()).isEqualTo("Av. Norte, 1500");
        assertThat(shown.allowedActions()).containsExactly(PatientAction.CONFIRM, PatientAction.CANCEL, PatientAction.WITHDRAW);
        verify(audit).patientAction(eq(EngagementAudit.LINK_VIEWED), eq(view.id()), any(), eq("10.0.0.9"));
    }

    @Test
    @DisplayName("confirmado não oferece CONFIRM; cancelar devolve backToQueue e registra o uso do token")
    void shouldDelegateActionsAndAudit() {
        AppointmentView view = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        String token = tokenFor(view, view.startAt(), TokenPurpose.APPOINTMENT);
        AppointmentView cancelled = new AppointmentView(view.id(), view.slotId(), view.referralId(), view.patientId(),
                UNIT, SPECIALTY, view.startAt(), AppointmentOrigin.REGULAR, AppointmentStatus.CANCELLED_BY_PATIENT,
                view.confirmationDeadline());
        when(scheduling.confirm(view.id())).thenReturn(view);
        when(scheduling.cancelByPatient(view.id())).thenReturn(cancelled);
        when(scheduling.withdraw(view.id())).thenReturn(cancelled);

        assertThat(service.confirm(token, null).backToQueue()).isFalse();
        var result = service.cancel(token, null);
        service.withdraw(token, null);

        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED_BY_PATIENT);
        assertThat(result.backToQueue()).isTrue();
        verify(tokens, org.mockito.Mockito.times(3)).save(org.mockito.ArgumentMatchers.argThat(t -> t.lastUsedAt() != null));
        verify(audit).patientAction(eq(EngagementAudit.PATIENT_CANCELLED), eq(view.id()), any(), eq(null));
    }

    @Test
    @DisplayName("RF-26 CA1: link malformado ou desconhecido ⇒ 404; expirado ⇒ 410; oferta (F6) não serve aqui")
    void shouldRejectInvalidLinks() {
        AppointmentView view = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        String expired = tokenFor(view, NOW, TokenPurpose.APPOINTMENT);
        String offer = tokenFor(view, view.startAt(), TokenPurpose.OFFER);

        assertThatThrownBy(() -> service.view("curto", null)).isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PATIENT_LINK_NOT_FOUND");
        assertThatThrownBy(() -> service.confirm("AAAAAAAAAAAAAAAAAAAAAA", null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(offer, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.withdraw(expired, null)).isInstanceOf(GoneException.class)
                .extracting("code").isEqualTo("PATIENT_LINK_EXPIRED");
        verify(audit).rejectedLink("EXPIRED", null);
    }

    @Test
    @DisplayName("sem ações depois do início; prazo vencido tira só o CONFIRM")
    void shouldNarrowActionsOverTime() {
        AppointmentView view = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        var late = new PatientActionService(tokens, scheduling, patients, catalog, audit,
                java.time.Clock.fixed(view.confirmationDeadline().plus(Duration.ofMinutes(1)), CLOCK.getZone()));
        var issued = PatientActionToken.issue(TokenPurpose.APPOINTMENT, view.id(), view.startAt(), CLOCK);
        when(tokens.findByHash(issued.token().tokenHash())).thenReturn(Optional.of(issued.token()));
        when(scheduling.findAppointmentView(view.id())).thenReturn(Optional.of(view));
        when(patients.findSummary(any())).thenReturn(Optional.empty());
        when(catalog.findUnit(UNIT)).thenReturn(Optional.of(unit()));
        when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.empty());

        var shown = late.view(issued.raw(), null);

        assertThat(shown.allowedActions()).containsExactly(PatientAction.CANCEL, PatientAction.WITHDRAW);
        assertThat(shown.label()).isEqualTo("consulta");
        assertThat(shown.firstName()).isEmpty();
    }

    @Test
    @DisplayName("RF-28: SCHEDULER só vê o log de um agendamento da própria unidade; ADMIN vê tudo; sandbox limitado a 50")
    void shouldScopeNotificationQueries() {
        var queries = new NotificationQueryService(notifications, scheduling);
        AppointmentView view = appointment(AppointmentStatus.PENDING_CONFIRMATION);
        var scheduler = new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UNIT, "Sônia");
        var otherScheduler = new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UUID.randomUUID(), "Outra");
        when(scheduling.findAppointmentView(view.id())).thenReturn(Optional.of(view));
        when(notifications.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        queries.list(new NotificationFilter(view.id(), null), PageRequest.of(0, 20), scheduler);
        queries.list(new NotificationFilter(null, UUID.randomUUID()), PageRequest.of(0, 20),
                new CurrentUser(UUID.randomUUID(), Role.ADMIN, null, "Admin"));
        assertThatThrownBy(() -> queries.list(new NotificationFilter(view.id(), null), PageRequest.of(0, 20), otherScheduler))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> queries.list(new NotificationFilter(null, null), PageRequest.of(0, 20), scheduler))
                .isInstanceOf(ForbiddenOperationException.class);

        UUID patient = UUID.randomUUID();
        queries.sandboxInbox(patient, 500);
        queries.sandboxInbox(patient, 0);
        verify(notifications).findSandbox(patient, 50);
        verify(notifications).findSandbox(patient, 1);
    }
}
