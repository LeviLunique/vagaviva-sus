package br.com.vagaviva.engagement.application.service;

import static br.com.vagaviva.engagement.fixtures.EngagementFixture.CLOCK;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.NOW;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.SPECIALTY;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.UNIT;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.patient;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.specialty;
import static br.com.vagaviva.engagement.fixtures.EngagementFixture.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.PatientAction;
import br.com.vagaviva.engagement.application.port.out.PatientActionTokenRepository;
import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.engagement.domain.TokenPurpose;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.OfferView;
import br.com.vagaviva.reallocation.ReallocationApi;
import br.com.vagaviva.reallocation.ReallocationApi.AcceptedOffer;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientOfferActionsTest {

    private static final Instant EXPIRES = NOW.plus(Duration.ofHours(4));

    @Mock PatientActionTokenRepository tokens;
    @Mock SchedulingApi scheduling;
    @Mock ReallocationApi reallocation;
    @Mock PatientApi patients;
    @Mock CatalogApi catalog;
    @Mock EngagementAudit audit;

    private PatientActionService service;

    @BeforeEach
    void setUp() {
        service = new PatientActionService(tokens, scheduling, reallocation, patients, catalog, audit, CLOCK);
        lenient().when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static OfferView offer(OfferStatus status) {
        return new OfferView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UNIT, SPECIALTY,
                NOW.plus(Duration.ofHours(26)), EXPIRES, 1, status);
    }

    private String tokenFor(UUID subject, TokenPurpose purpose) {
        var issued = PatientActionToken.issue(purpose, subject, EXPIRES, CLOCK);
        lenient().when(tokens.findByHash(issued.token().tokenHash())).thenReturn(Optional.of(issued.token()));
        return issued.raw();
    }

    @Test
    @DisplayName("RF-31: link da oferta mostra rótulo genérico, prazo de resposta e as ações aceitar/recusar")
    void shouldShowOffer() {
        OfferView view = offer(OfferStatus.PENDING);
        String token = tokenFor(view.id(), TokenPurpose.OFFER);
        when(reallocation.findOfferView(view.id())).thenReturn(Optional.of(view));
        when(patients.findSummary(view.patientId())).thenReturn(Optional.of(patient(view.patientId(), false, true)));
        when(catalog.findUnit(UNIT)).thenReturn(Optional.of(unit()));
        when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.of(specialty(SpecialtyType.CONSULTATION, true)));

        var shown = service.view(token, "10.0.0.7");

        assertThat(shown.kind()).isEqualTo("OFFER");
        assertThat(shown.label()).isEqualTo("consulta");
        assertThat(shown.expiresAt()).isEqualTo(EXPIRES);
        assertThat(shown.confirmationDeadline()).isNull();
        assertThat(shown.status()).isEqualTo("PENDING");
        assertThat(shown.allowedActions()).containsExactly(PatientAction.ACCEPT_OFFER, PatientAction.DECLINE_OFFER);
        verify(audit).offerAction(EngagementAudit.LINK_VIEWED, view.id(), tokenIdOf(token), "10.0.0.7");
    }

    @Test
    @DisplayName("oferta já encerrada: o link ainda abre, mas sem ações")
    void shouldShowClosedOfferWithoutActions() {
        OfferView view = offer(OfferStatus.SUPERSEDED);
        String token = tokenFor(view.id(), TokenPurpose.OFFER);
        when(reallocation.findOfferView(view.id())).thenReturn(Optional.of(view));
        when(patients.findSummary(any())).thenReturn(Optional.empty());
        when(catalog.findUnit(UNIT)).thenReturn(Optional.of(unit()));
        when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.empty());

        assertThat(service.view(token, null).allowedActions()).isEmpty();
    }

    @Test
    @DisplayName("RF-32: aceitar ⇒ CONFIRMED com o appointmentId; recusar ⇒ DECLINED; ambos auditados")
    void shouldAcceptAndDecline() {
        OfferView view = offer(OfferStatus.PENDING);
        String token = tokenFor(view.id(), TokenPurpose.OFFER);
        UUID appointment = UUID.randomUUID();
        when(reallocation.accept(view.id())).thenReturn(new AcceptedOffer(view.id(), appointment));
        when(reallocation.decline(view.id())).thenReturn(offer(OfferStatus.DECLINED));

        var accepted = service.acceptOffer(token, "10.0.0.7");
        var declined = service.declineOffer(token, null);

        assertThat(accepted.status()).isEqualTo("CONFIRMED");
        assertThat(accepted.appointmentId()).isEqualTo(appointment);
        assertThat(declined.status()).isEqualTo("DECLINED");
        assertThat(declined.appointmentId()).isNull();
        verify(audit).offerAction(eq(EngagementAudit.PATIENT_ACCEPTED_OFFER), eq(view.id()), any(), eq("10.0.0.7"));
        verify(audit).offerAction(eq(EngagementAudit.PATIENT_DECLINED_OFFER), any(), any(), eq(null));
    }

    @Test
    @DisplayName("link de agendamento não aceita oferta (e vice-versa) ⇒ 404")
    void shouldRejectWrongPurpose() {
        String appointmentToken = tokenFor(UUID.randomUUID(), TokenPurpose.APPOINTMENT);

        assertThatThrownBy(() -> service.acceptOffer(appointmentToken, null)).isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PATIENT_LINK_NOT_FOUND");
        assertThatThrownBy(() -> service.declineOffer(appointmentToken, null)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(reallocation);
    }

    private UUID tokenIdOf(String raw) {
        return tokens.findByHash(PatientActionToken.hash(raw)).orElseThrow().id();
    }
}
