package br.com.vagaviva.scheduling.adapter.out.persistence;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.domain.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
class AppointmentEntity {

    @Id
    private UUID id;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AppointmentStatus status;

    @Column(name = "confirmation_deadline")
    private Instant confirmationDeadline;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "outcome_at")
    private Instant outcomeAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected AppointmentEntity() {
    }

    static AppointmentEntity from(Appointment a) {
        var entity = new AppointmentEntity();
        entity.id = a.id();
        entity.slotId = a.slotId();
        entity.referralId = a.referralId();
        entity.patientId = a.patientId();
        entity.unitId = a.unitId();
        entity.specialtyId = a.specialtyId();
        entity.startAt = a.startAt();
        entity.origin = a.origin();
        entity.status = a.status();
        entity.confirmationDeadline = a.confirmationDeadline();
        entity.confirmedAt = a.confirmedAt();
        entity.cancelledAt = a.cancelledAt();
        entity.outcomeAt = a.outcomeAt();
        entity.createdAt = a.createdAt();
        entity.updatedAt = a.updatedAt();
        entity.version = a.version();
        return entity;
    }

    Appointment toDomain() {
        return Appointment.restore(id, slotId, referralId, patientId, unitId, specialtyId, startAt, origin, status,
                confirmationDeadline, confirmedAt, cancelledAt, outcomeAt, createdAt, updatedAt, version);
    }
}
