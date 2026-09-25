package br.com.vagaviva.shared.domain;

/** Identidade não comprovada, como credenciais inválidas (401). */
public class UnauthenticatedException extends DomainException {

    public UnauthenticatedException(String code, String message) {
        super(code, message);
    }
}
