package br.com.vagaviva.shared.domain;

/**
 * Violação de regra de negócio com código estável ({@code UPPER_SNAKE_CASE}) e mensagem em
 * português para o usuário. Cada subclasse corresponde a um status HTTP (mapeado em
 * {@code GlobalExceptionHandler}); o domínio não conhece HTTP.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
