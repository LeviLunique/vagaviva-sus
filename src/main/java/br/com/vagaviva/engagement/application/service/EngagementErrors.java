package br.com.vagaviva.engagement.application.service;

import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.NotFoundException;

/** Erros de negócio do engajamento (códigos estáveis da API). */
final class EngagementErrors {

    private EngagementErrors() {
    }

    static NotFoundException linkNotFound() {
        return new NotFoundException("PATIENT_LINK_NOT_FOUND", "Link inválido. Confira a mensagem recebida.");
    }

    static GoneException linkExpired() {
        return new GoneException("PATIENT_LINK_EXPIRED", "Este link expirou.");
    }

    static ForbiddenOperationException outOfUnit() {
        return new ForbiddenOperationException("NOTIFICATIONS_OUT_OF_UNIT",
                "Informe um agendamento da sua unidade para consultar as notificações.");
    }
}
