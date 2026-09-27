package br.com.vagaviva.shared.domain;

/** Recurso inexistente (404). */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
