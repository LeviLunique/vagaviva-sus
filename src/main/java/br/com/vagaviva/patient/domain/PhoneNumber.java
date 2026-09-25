package br.com.vagaviva.patient.domain;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.util.regex.Pattern;

/** Celular brasileiro em E.164: {@code +55} + DDD (dois dígitos de 1 a 9) + 9 dígitos começando em 9. */
public record PhoneNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("\\+55[1-9]{2}9\\d{8}");

    public PhoneNumber {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new BusinessRuleException("INVALID_PHONE",
                    "Telefone inválido: informe um celular no formato +55DDD9XXXXXXXX.");
        }
    }

    /** Aceita espaços, parênteses e hífens de digitação ({@code +55 (11) 98765-4321}). */
    public static PhoneNumber of(String value) {
        return new PhoneNumber(value == null ? null : value.replaceAll("[\\s()-]", ""));
    }
}
