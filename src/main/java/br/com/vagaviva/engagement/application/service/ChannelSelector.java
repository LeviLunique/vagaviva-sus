package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.engagement.application.service.EngagementProperties.ChannelMode;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.patient.PatientSummary;
import org.springframework.stereotype.Component;

/**
 * Canal efetivo: em {@code SANDBOX} tudo fica no sandbox; em {@code LIVE}, WhatsApp para quem deu
 * aceite (se o canal estiver habilitado), senão SMS (se habilitado), senão sandbox.
 */
@Component
class ChannelSelector {

    private final EngagementProperties properties;

    ChannelSelector(EngagementProperties properties) {
        this.properties = properties;
    }

    NotificationChannel channelFor(PatientSummary patient) {
        if (properties.channelMode() == ChannelMode.SANDBOX) {
            return NotificationChannel.SANDBOX;
        }
        if (patient.whatsappOptIn() && properties.whatsapp().enabled()) {
            return NotificationChannel.WHATSAPP;
        }
        return properties.sms().enabled() ? NotificationChannel.SMS : NotificationChannel.SANDBOX;
    }
}
