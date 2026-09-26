package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases;
import br.com.vagaviva.engagement.application.port.out.PatientActionTokenRepository;
import br.com.vagaviva.engagement.domain.CareLabel;
import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.engagement.domain.TokenPurpose;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-26: o link do paciente. Token inexistente ⇒ 404; expirado ⇒ 410 — ambos auditados sem expor
 * nada do agendamento. As ações delegam à agenda, que aplica prazo, estado e liberação da vaga.
 */
@Service
class PatientActionService implements PatientActionUseCases {

    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final PatientActionTokenRepository tokens;
    private final SchedulingApi scheduling;
    private final PatientApi patients;
    private final CatalogApi catalog;
    private final EngagementAudit audit;
    private final Clock clock;

    PatientActionService(PatientActionTokenRepository tokens, SchedulingApi scheduling, PatientApi patients,
            CatalogApi catalog, EngagementAudit audit, Clock clock) {
        this.tokens = tokens;
        this.scheduling = scheduling;
        this.patients = patients;
        this.catalog = catalog;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public PatientAppointmentView view(String token, @Nullable String clientIp) {
        PatientActionToken actionToken = resolve(token, clientIp);
        AppointmentView appointment = scheduling.findAppointmentView(actionToken.subjectId())
                .orElseThrow(EngagementErrors::linkNotFound);
        String firstName = patients.findSummary(appointment.patientId()).map(PatientSummary::firstName).orElse("");
        HealthUnitSummary unit = catalog.findUnit(appointment.unitId()).orElseThrow();
        CareLabel label = catalog.findSpecialty(appointment.specialtyId())
                .map(s -> s.type() == SpecialtyType.EXAM ? CareLabel.EXAM : CareLabel.CONSULTATION)
                .orElse(CareLabel.CONSULTATION);
        audit.patientAction(EngagementAudit.LINK_VIEWED, appointment.id(), actionToken.id(), clientIp);
        return new PatientAppointmentView("APPOINTMENT", firstName, label.noun(), appointment.startAt(), unit.name(),
                unit.address(), appointment.status(), appointment.confirmationDeadline(), allowedActions(appointment));
    }

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public ActionResult confirm(String token, @Nullable String clientIp) {
        return act(token, clientIp, EngagementAudit.PATIENT_CONFIRMED, scheduling::confirm, false);
    }

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public ActionResult cancel(String token, @Nullable String clientIp) {
        return act(token, clientIp, EngagementAudit.PATIENT_CANCELLED, scheduling::cancelByPatient, true);
    }

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public ActionResult withdraw(String token, @Nullable String clientIp) {
        return act(token, clientIp, EngagementAudit.PATIENT_WITHDREW, scheduling::withdraw, false);
    }

    private ActionResult act(String token, @Nullable String clientIp, String auditAction,
            Function<UUID, AppointmentView> action, boolean backToQueue) {
        PatientActionToken actionToken = resolve(token, clientIp);
        AppointmentView result = action.apply(actionToken.subjectId());
        actionToken.markUsed(clock);
        tokens.save(actionToken);
        audit.patientAction(auditAction, result.id(), actionToken.id(), clientIp);
        return new ActionResult(result.status(), backToQueue);
    }

    private PatientActionToken resolve(String token, @Nullable String clientIp) {
        PatientActionToken actionToken = token != null && TOKEN_FORMAT.matcher(token).matches()
                ? tokens.findByHash(PatientActionToken.hash(token)).filter(t -> t.purpose() == TokenPurpose.APPOINTMENT)
                        .orElse(null)
                : null;
        if (actionToken == null) {
            audit.rejectedLink("NOT_FOUND", clientIp);
            throw EngagementErrors.linkNotFound();
        }
        if (actionToken.isExpired(clock)) {
            audit.rejectedLink("EXPIRED", clientIp);
            throw EngagementErrors.linkExpired();
        }
        return actionToken;
    }

    /** RN-12: confirmar até o prazo; cancelar e desistir até o início. */
    private List<PatientAction> allowedActions(AppointmentView appointment) {
        Instant now = clock.instant();
        List<PatientAction> actions = new ArrayList<>();
        boolean open = appointment.status().isOpen() && now.isBefore(appointment.startAt());
        if (appointment.status() == AppointmentStatus.PENDING_CONFIRMATION && appointment.confirmationDeadline() != null
                && !now.isAfter(appointment.confirmationDeadline())) {
            actions.add(PatientAction.CONFIRM);
        }
        if (open) {
            actions.add(PatientAction.CANCEL);
            actions.add(PatientAction.WITHDRAW);
        }
        return actions;
    }
}
