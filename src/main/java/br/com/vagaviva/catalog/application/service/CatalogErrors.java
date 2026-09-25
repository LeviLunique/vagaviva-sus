package br.com.vagaviva.catalog.application.service;

import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;

/** Erros de negócio do catálogo (códigos estáveis da API). */
final class CatalogErrors {

    private CatalogErrors() {
    }

    static NotFoundException unitNotFound() {
        return new NotFoundException("HEALTH_UNIT_NOT_FOUND", "Unidade de saúde não encontrada.");
    }

    static NotFoundException specialtyNotFound() {
        return new NotFoundException("SPECIALTY_NOT_FOUND", "Especialidade não encontrada.");
    }

    static ConflictException cnesAlreadyRegistered() {
        return new ConflictException("CNES_ALREADY_REGISTERED", "Já existe uma unidade com este CNES.");
    }

    static ConflictException specialtyCodeAlreadyRegistered() {
        return new ConflictException("SPECIALTY_CODE_ALREADY_REGISTERED", "Já existe uma especialidade com este código.");
    }
}
