package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.NotFoundException;

/** Erros de negócio da regulação (códigos estáveis da API). */
final class RegulationErrors {

    private RegulationErrors() {
    }

    static NotFoundException referralNotFound() {
        return new NotFoundException("REFERRAL_NOT_FOUND", "Encaminhamento não encontrado.");
    }

    static ConflictException duplicated() {
        return new ConflictException("REFERRAL_DUPLICATED",
                "O paciente já possui encaminhamento ativo para esta especialidade.");
    }

    static ForbiddenOperationException outOfUnit() {
        return new ForbiddenOperationException("REFERRAL_OUT_OF_UNIT",
                "O encaminhamento pertence a outra unidade de saúde.");
    }

    static ForbiddenOperationException unitRequired() {
        return new ForbiddenOperationException("HEALTH_UNIT_REQUIRED", "Seu usuário não está vinculado a uma unidade.");
    }

    static BusinessRuleException invalidPatient() {
        return new BusinessRuleException("INVALID_PATIENT", "Paciente inexistente ou inativo.");
    }

    static BusinessRuleException invalidSpecialty() {
        return new BusinessRuleException("INVALID_SPECIALTY", "Especialidade inexistente ou inativa.");
    }

    static BusinessRuleException riskClassRequired() {
        return new BusinessRuleException("RISK_CLASS_REQUIRED", "Para aprovar, informe a classe de risco.");
    }

    static NotFoundException specialtyNotFound() {
        return new NotFoundException("SPECIALTY_NOT_FOUND", "Especialidade não encontrada.");
    }

    static NotFoundException publicPositionNotFound() {
        return new NotFoundException("QUEUE_POSITION_NOT_FOUND",
                "Não encontramos encaminhamento com estes dados. Confira o protocolo e a data de nascimento.");
    }
}
