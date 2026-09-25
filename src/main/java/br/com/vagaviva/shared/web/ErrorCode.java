package br.com.vagaviva.shared.web;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/**
 * Códigos genéricos de erro da API com o título (em português) e o status de cada um. Erros de
 * regra de negócio usam o código da própria {@code DomainException} no {@code type}, com o
 * título da família correspondente.
 */
public enum ErrorCode {

    MALFORMED_REQUEST(400, "Requisição malformada"),
    UNAUTHENTICATED(401, "Não autenticado"),
    ACCESS_DENIED(403, "Acesso negado"),
    NOT_FOUND(404, "Recurso não encontrado"),
    METHOD_NOT_ALLOWED(405, "Método não suportado"),
    CONFLICT(409, "Conflito"),
    GONE(410, "Recurso expirado"),
    UNSUPPORTED_MEDIA_TYPE(415, "Tipo de conteúdo não suportado"),
    VALIDATION_FAILED(422, "Dados inválidos"),
    BUSINESS_RULE_VIOLATED(422, "Regra de negócio violada"),
    INTERNAL_ERROR(500, "Erro interno");

    private static final String TYPE_BASE = "https://vagaviva.dev/problems/";

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return typeOf(name());
    }

    /** {@code EMAIL_ALREADY_REGISTERED} ⇒ {@code https://vagaviva.dev/problems/email-already-registered}. */
    public static URI typeOf(String code) {
        return URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** Primeiro código genérico do status, ou {@link #INTERNAL_ERROR} para status sem mapeamento. */
    public static ErrorCode fromStatus(int status) {
        return Arrays.stream(values())
                .filter(code -> code.status == status)
                .findFirst()
                .orElse(INTERNAL_ERROR);
    }
}
