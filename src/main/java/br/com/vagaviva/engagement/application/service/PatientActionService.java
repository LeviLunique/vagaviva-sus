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
import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.OfferView;
import br.com.vagaviva.reallocation.ReallocationApi;
import br.com.vagaviva.reallocation.ReallocationApi.AcceptedOffer;
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
 * RF-26/RF-32: o link do paciente. Token inexistente (ou de outro tipo) ⇒ 404; expirado ⇒ 410 —
 * ambos auditados sem expor nada. As ações delegam à agenda (agendamento) ou ao encaixe (oferta).
 */
@Service
class PatientActionService implements PatientActionUseCases {

    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final PatientActionTokenRepository tokens;
    private final SchedulingApi scheduling;
    private final ReallocationApi reallocation;
    private final PatientApi patients;
    private final CatalogApi catalog;
    private final EngagementAudit audit;
    private final Clock clock;

    PatientActionService(PatientActionTokenRepository tokens, SchedulingApi scheduling, ReallocationApi reallocation,
            PatientApi patients, CatalogApi catalog, EngagementAudit audit, Clock clock) {
        this.tokens = tokens;
        this.scheduling = scheduling;
        this.reallocation = reallocation;
        this.patients = patients;
        this.catalog = catalog;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public PatientLinkView view(String token, @Nullable String clientIp) {
        PatientActionToken actionToken = resolve(token, clientIp, null);
        return actionToken.purpose() == TokenPurpose.OFFER
                ? offerView(actionToken, clientIp)
                : appointmentView(actionToken, clientIp);
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

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public OfferActionResult acceptOffer(String token, @Nullable String clientIp) {
        PatientActionToken actionToken = resolve(token, clientIp, TokenPurpose.OFFER);
        AcceptedOffer accepted = reallocation.accept(actionToken.subjectId());
        used(actionToken);
        audit.offerAction(EngagementAudit.PATIENT_ACCEPTED_OFFER, accepted.offerId(), actionToken.id(), clientIp);
        return new OfferActionResult(AppointmentStatus.CONFIRMED.name(), accepted.appointmentId());
    }

    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, GoneException.class})
    public OfferActionResult declineOffer(String token, @Nullable String clientIp) {
        PatientActionToken actionToken = resolve(token, clientIp, TokenPurpose.OFFER);
        OfferView declined = reallocation.decline(actionToken.subjectId());
        used(actionToken);
        audit.offerAction(EngagementAudit.PATIENT_DECLINED_OFFER, declined.id(), actionToken.id(), clientIp);
        return new OfferActionResult(declined.status().name(), null);
    }

    private PatientLinkView appointmentView(PatientActionToken actionToken, @Nullable String clientIp) {
        AppointmentView appointment = scheduling.findAppointmentView(actionToken.subjectId())
                .orElseThrow(EngagementErrors::linkNotFound);
        HealthUnitSummary unit = catalog.findUnit(appointment.unitId()).orElseThrow();
        audit.patientAction(EngagementAudit.LINK_VIEWED, appointment.id(), actionToken.id(), clientIp);
        return new PatientLinkView(TokenPurpose.APPOINTMENT.name(), firstName(appointment.patientId()),
                labelOf(appointment.specialtyId()).noun(), appointment.startAt(), unit.name(), unit.address(),
                appointment.status().name(), appointment.confirmationDeadline(), null, allowedActions(appointment));
    }

    private PatientLinkView offerView(PatientActionToken actionToken, @Nullable String clientIp) {
        OfferView offer = reallocation.findOfferView(actionToken.subjectId()).orElseThrow(EngagementErrors::linkNotFound);
        HealthUnitSummary unit = catalog.findUnit(offer.unitId()).orElseThrow();
        audit.offerAction(EngagementAudit.LINK_VIEWED, offer.id(), actionToken.id(), clientIp);
        List<PatientAction> actions = offer.status() == OfferStatus.PENDING && clock.instant().isBefore(offer.expiresAt())
                ? List.of(PatientAction.ACCEPT_OFFER, PatientAction.DECLINE_OFFER)
                : List.of();
        return new PatientLinkView(TokenPurpose.OFFER.name(), firstName(offer.patientId()),
                labelOf(offer.specialtyId()).noun(), offer.startAt(), unit.name(), unit.address(),
                offer.status().name(), null, offer.expiresAt(), actions);
    }

    private ActionResult act(String token, @Nullable String clientIp, String auditAction,
            Function<UUID, AppointmentView> action, boolean backToQueue) {
        PatientActionToken actionToken = resolve(token, clientIp, TokenPurpose.APPOINTMENT);
        AppointmentView result = action.apply(actionToken.subjectId());
        used(actionToken);
        audit.patientAction(auditAction, result.id(), actionToken.id(), clientIp);
        return new ActionResult(result.status(), backToQueue);
    }

    private void used(PatientActionToken actionToken) {
        actionToken.markUsed(clock);
        tokens.save(actionToken);
    }

    /** @param purpose tipo exigido pela ação, ou {@code null} para aceitar qualquer um (visualização) */
    private PatientActionToken resolve(String token, @Nullable String clientIp, @Nullable TokenPurpose purpose) {
        PatientActionToken actionToken = token != null && TOKEN_FORMAT.matcher(token).matches()
                ? tokens.findByHash(PatientActionToken.hash(token))
                        .filter(t -> purpose == null || t.purpose() == purpose).orElse(null)
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

    private String firstName(UUID patientId) {
        return patients.findSummary(patientId).map(PatientSummary::firstName).orElse("");
    }

    private CareLabel labelOf(UUID specialtyId) {
        return catalog.findSpecialty(specialtyId)
                .map(s -> s.type() == SpecialtyType.EXAM ? CareLabel.EXAM : CareLabel.CONSULTATION)
                .orElse(CareLabel.CONSULTATION);
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
