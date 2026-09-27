package br.com.vagaviva.reallocation.application.service;

import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.NotFoundException;

final class ReallocationErrors {

    private ReallocationErrors() {
    }

    static NotFoundException offerNotFound() {
        return new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada.");
    }

    static ForbiddenOperationException outOfUnit() {
        return new ForbiddenOperationException("OFFERS_OUT_OF_UNIT",
                "Informe uma vaga da sua unidade para consultar as ofertas.");
    }
}
