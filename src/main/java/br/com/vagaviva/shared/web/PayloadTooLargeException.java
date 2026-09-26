package br.com.vagaviva.shared.web;

/** Corpo da requisição acima do limite configurado ({@code vagaviva.http.max-request-body}) ⇒ 413. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(long limitBytes) {
        super("O corpo da requisição passa do limite de %d KB.".formatted(limitBytes / 1024));
    }
}
