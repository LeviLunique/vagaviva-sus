package br.com.vagaviva.shared.domain;

/** Conflito com o estado atual ou duplicidade (409). */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
