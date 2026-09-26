package br.com.vagaviva.engagement.domain;

/** Marcos em que o paciente é avisado (RF-24). */
public enum NotificationType {
    REFERRAL_QUEUED,
    APPOINTMENT_SCHEDULED,
    CONFIRMATION_REMINDER,
    ATTENDANCE_REMINDER,
    APPOINTMENT_CANCELLED_BY_UNIT,
    SLOT_OFFER
}
