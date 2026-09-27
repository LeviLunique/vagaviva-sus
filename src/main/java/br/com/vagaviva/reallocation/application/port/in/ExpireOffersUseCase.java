package br.com.vagaviva.reallocation.application.port.in;

/** RN-17: ofertas sem resposta no prazo expiram; rodada encerrada sem aceite ⇒ próxima rodada. */
public interface ExpireOffersUseCase {

    /** @return quantas vagas tiveram ofertas expiradas nesta execução */
    int expireOverdue();
}
