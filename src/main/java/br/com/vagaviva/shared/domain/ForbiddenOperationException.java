package br.com.vagaviva.shared.domain;

/** Operação não permitida para o usuário, como fora da sua unidade (403). */
public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }
}
