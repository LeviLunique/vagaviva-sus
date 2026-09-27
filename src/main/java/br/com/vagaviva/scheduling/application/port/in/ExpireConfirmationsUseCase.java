package br.com.vagaviva.scheduling.application.port.in;

import java.util.UUID;

/** RF-27 / RN-13: agendamentos sem confirmação até o prazo liberam a vaga e voltam à fila. */
public interface ExpireConfirmationsUseCase {

    /** @return quantos agendamentos expiraram nesta execução */
    int expireOverdue();

    /** Recurso de demonstração (RF-29): antecipa o prazo do agendamento e o expira agora. */
    void expireNow(UUID appointmentId);
}
