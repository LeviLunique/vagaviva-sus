package br.com.vagaviva.shared.domain;

/** Recurso que existiu mas expirou, como um link do paciente (410). */
public class GoneException extends DomainException {

    public GoneException(String code, String message) {
        super(code, message);
    }
}
