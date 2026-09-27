package br.com.vagaviva.engagement.domain;

/** Canal efetivo da mensagem. {@code SANDBOX} só registra (demonstração e ambientes sem provedor). */
public enum NotificationChannel {
    SANDBOX,
    SMS,
    WHATSAPP
}
