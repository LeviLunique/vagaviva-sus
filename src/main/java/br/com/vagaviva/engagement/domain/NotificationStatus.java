package br.com.vagaviva.engagement.domain;

public enum NotificationStatus {
    PENDING,
    SENT,
    /** Desistiu após {@link Notification#MAX_ATTEMPTS} tentativas (a mensagem também vai para a DLQ). */
    FAILED
}
