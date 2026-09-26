package br.com.vagaviva.scheduling.domain;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Agendamento de um paciente da fila em uma vaga (SPEC §5.2). */
public final class Appointment {

    private final UUID id;
    private final UUID slotId;
    private final UUID referralId;
    private final UUID patientId;
    private final UUID unitId;
    private final UUID specialtyId;
    private final Instant startAt;
    private final AppointmentOrigin origin;
    private AppointmentStatus status;
    private @Nullable Instant confirmationDeadline;
    private @Nullable Instant confirmedAt;
    private @Nullable Instant cancelledAt;
    private @Nullable Instant outcomeAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final @Nullable Long version;

    private Appointment(UUID id, UUID slotId, UUID referralId, UUID patientId, UUID unitId, UUID specialtyId,
            Instant startAt, AppointmentOrigin origin, AppointmentStatus status, @Nullable Instant confirmationDeadline,
            @Nullable Instant confirmedAt, @Nullable Instant cancelledAt, @Nullable Instant outcomeAt,
            Instant createdAt, Instant updatedAt, @Nullable Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.slotId = Objects.requireNonNull(slotId, "slotId");
        this.referralId = Objects.requireNonNull(referralId, "referralId");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.unitId = Objects.requireNonNull(unitId, "unitId");
        this.specialtyId = Objects.requireNonNull(specialtyId, "specialtyId");
        this.startAt = Objects.requireNonNull(startAt, "startAt");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.status = Objects.requireNonNull(status, "status");
        this.confirmationDeadline = confirmationDeadline;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
        this.outcomeAt = outcomeAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /** RF-20: alocação pela fila ⇒ aguardando confirmação até o prazo da RN-11. */
    public static Appointment schedule(Slot slot, UUID referralId, UUID patientId, ConfirmationPolicy policy,
            Clock clock) {
        Instant now = clock.instant();
        AppointmentOrigin origin = slot.wasReleased() ? AppointmentOrigin.REALLOCATED : AppointmentOrigin.REGULAR;
        return new Appointment(Ids.newId(), slot.id(), referralId, patientId, slot.unitId(), slot.specialtyId(),
                slot.startAt(), origin, AppointmentStatus.PENDING_CONFIRMATION, policy.deadlineFor(slot.startAt()), null,
                null, null, now, now, null);
    }

    public static Appointment restore(UUID id, UUID slotId, UUID referralId, UUID patientId, UUID unitId,
            UUID specialtyId, Instant startAt, AppointmentOrigin origin, AppointmentStatus status,
            @Nullable Instant confirmationDeadline, @Nullable Instant confirmedAt, @Nullable Instant cancelledAt,
            @Nullable Instant outcomeAt, Instant createdAt, Instant updatedAt, @Nullable Long version) {
        return new Appointment(id, slotId, referralId, patientId, unitId, specialtyId, startAt, origin, status,
                confirmationDeadline, confirmedAt, cancelledAt, outcomeAt, createdAt, updatedAt, version);
    }

    /** RN-19: check-in só na data civil do atendimento (fuso do relógio de negócio). */
    public void checkIn(Clock clock) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(startAt.atZone(clock.getZone()).toLocalDate())) {
            throw new BusinessRuleException("CHECK_IN_OUTSIDE_DAY", "O check-in só pode ser feito no dia do atendimento.");
        }
        moveTo(AppointmentStatus.ATTENDED);
        outcomeAt = clock.instant();
        updatedAt = outcomeAt;
    }

    /** RN-19: falta só depois do horário de início. */
    public void markNoShow(Clock clock) {
        Instant now = clock.instant();
        if (now.isBefore(startAt)) {
            throw new BusinessRuleException("NO_SHOW_BEFORE_START", "A falta só pode ser registrada após o horário de início.");
        }
        moveTo(AppointmentStatus.NO_SHOW);
        outcomeAt = now;
        updatedAt = now;
    }

    /**
     * RN-12: confirma até o prazo. Confirmar de novo é aceito (idempotente) e não muda nada.
     *
     * @return {@code true} se mudou de estado nesta chamada
     */
    public boolean confirm(Clock clock) {
        if (status == AppointmentStatus.CONFIRMED) {
            return false;
        }
        Instant now = clock.instant();
        if (status == AppointmentStatus.PENDING_CONFIRMATION && confirmationDeadline != null
                && now.isAfter(confirmationDeadline)) {
            throw new BusinessRuleException("CONFIRMATION_DEADLINE_PASSED", "O prazo para confirmar já terminou.");
        }
        moveTo(AppointmentStatus.CONFIRMED);
        confirmedAt = now;
        updatedAt = now;
        return true;
    }

    /** RN-12: o paciente cancela até o início. Idempotente. */
    public boolean cancelByPatient(Clock clock) {
        return leaveBeforeStart(AppointmentStatus.CANCELLED_BY_PATIENT, clock);
    }

    /** RF-26: o paciente desiste do atendimento até o início. Idempotente. */
    public boolean withdraw(Clock clock) {
        return leaveBeforeStart(AppointmentStatus.WITHDRAWN, clock);
    }

    /** RF-27: prazo vencido sem confirmação. */
    public void expireUnconfirmed(Clock clock) {
        Instant now = clock.instant();
        if (confirmationDeadline == null || !now.isAfter(confirmationDeadline)) {
            throw new BusinessRuleException("CONFIRMATION_DEADLINE_NOT_PASSED", "O prazo de confirmação ainda não venceu.");
        }
        moveTo(AppointmentStatus.EXPIRED_UNCONFIRMED);
        updatedAt = now;
    }

    /** Recurso de demonstração (RF-29): antecipa o prazo de confirmação para o instante informado. */
    public void anticipateDeadline(Instant newDeadline) {
        if (status != AppointmentStatus.PENDING_CONFIRMATION) {
            throw new ConflictException("APPOINTMENT_INVALID_STATE", "Só agendamentos aguardando confirmação têm prazo.");
        }
        confirmationDeadline = newDeadline;
    }

    private boolean leaveBeforeStart(AppointmentStatus target, Clock clock) {
        if (status == target) {
            return false;
        }
        Instant now = clock.instant();
        if (!now.isBefore(startAt) && status.isOpen()) {
            throw new BusinessRuleException("APPOINTMENT_ALREADY_STARTED", "O horário do atendimento já começou.");
        }
        moveTo(target);
        cancelledAt = now;
        updatedAt = now;
        return true;
    }

    /** RF-23: a unidade cancelou a vaga. */
    public void cancelByUnit(Clock clock) {
        moveTo(AppointmentStatus.CANCELLED_BY_UNIT);
        cancelledAt = clock.instant();
        updatedAt = cancelledAt;
    }

    public boolean belongsToUnit(@Nullable UUID unit) {
        return unitId.equals(unit);
    }

    private void moveTo(AppointmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("APPOINTMENT_INVALID_STATE",
                    "Operação não permitida para agendamento em %s.".formatted(status));
        }
        status = target;
    }

    public UUID id() {
        return id;
    }

    public UUID slotId() {
        return slotId;
    }

    public UUID referralId() {
        return referralId;
    }

    public UUID patientId() {
        return patientId;
    }

    public UUID unitId() {
        return unitId;
    }

    public UUID specialtyId() {
        return specialtyId;
    }

    public Instant startAt() {
        return startAt;
    }

    public AppointmentOrigin origin() {
        return origin;
    }

    public AppointmentStatus status() {
        return status;
    }

    public @Nullable Instant confirmationDeadline() {
        return confirmationDeadline;
    }

    public @Nullable Instant confirmedAt() {
        return confirmedAt;
    }

    public @Nullable Instant cancelledAt() {
        return cancelledAt;
    }

    public @Nullable Instant outcomeAt() {
        return outcomeAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Long version() {
        return version;
    }
}
