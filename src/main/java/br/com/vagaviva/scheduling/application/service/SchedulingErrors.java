package br.com.vagaviva.scheduling.application.service;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.NotFoundException;

/** Erros de negócio da agenda (códigos estáveis da API). */
final class SchedulingErrors {

    private SchedulingErrors() {
    }

    static NotFoundException slotNotFound() {
        return new NotFoundException("SLOT_NOT_FOUND", "Vaga não encontrada.");
    }

    static NotFoundException appointmentNotFound() {
        return new NotFoundException("APPOINTMENT_NOT_FOUND", "Agendamento não encontrado.");
    }

    static ForbiddenOperationException outOfUnit() {
        return new ForbiddenOperationException("SCHEDULING_OUT_OF_UNIT", "A vaga ou o agendamento pertence a outra unidade.");
    }

    static BusinessRuleException invalidUnit() {
        return new BusinessRuleException("INVALID_UNIT", "Unidade inexistente, inativa ou que não é executante (SPECIALIZED).");
    }

    static BusinessRuleException invalidSpecialty() {
        return new BusinessRuleException("INVALID_SPECIALTY", "Especialidade inexistente ou inativa.");
    }

    static BusinessRuleException overlap() {
        return new BusinessRuleException("SLOT_OVERLAP", "O profissional já tem vaga nesse horário.");
    }
}
