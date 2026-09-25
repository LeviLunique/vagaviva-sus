package br.com.vagaviva.regulation.events;

import java.util.UUID;

/** O encaminhamento foi aprovado e entrou na fila (o paciente será avisado — F5). */
public record ReferralQueued(UUID referralId, UUID patientId, String protocol) {
}
