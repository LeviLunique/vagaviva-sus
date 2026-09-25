package br.com.vagaviva.patient.application.service;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;

/** Erros de negócio de pacientes (códigos estáveis da API). */
final class PatientErrors {

    private PatientErrors() {
    }

    static NotFoundException notFound() {
        return new NotFoundException("PATIENT_NOT_FOUND", "Paciente não encontrado.");
    }

    static ConflictException cnsAlreadyRegistered() {
        return new ConflictException("CNS_ALREADY_REGISTERED", "Já existe um paciente com este CNS.");
    }

    static ConflictException cpfAlreadyRegistered() {
        return new ConflictException("CPF_ALREADY_REGISTERED", "Já existe um paciente com este CPF.");
    }

    static BusinessRuleException invalidSearchCriteria() {
        return new BusinessRuleException("INVALID_SEARCH_CRITERIA", "Informe exatamente um critério de busca: cns ou cpf.");
    }
}
