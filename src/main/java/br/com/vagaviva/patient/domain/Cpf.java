package br.com.vagaviva.patient.domain;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.util.regex.Pattern;

/** CPF com dígitos verificadores válidos (sequências repetidas como 111.111.111-11 são rejeitadas). */
public record Cpf(String value) {

    private static final Pattern FORMAT = Pattern.compile("\\d{11}");

    public Cpf {
        if (!isValid(value)) {
            throw new BusinessRuleException("INVALID_CPF", "CPF inválido: confira os dígitos verificadores.");
        }
    }

    public static Cpf of(String value) {
        return new Cpf(value == null ? null : value.replaceAll("[\\s.-]", ""));
    }

    static boolean isValid(String value) {
        if (value == null || !FORMAT.matcher(value).matches() || value.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(value, 9) == Character.digit(value.charAt(9), 10)
                && checkDigit(value, 10) == Character.digit(value.charAt(10), 10);
    }

    private static int checkDigit(String value, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Character.digit(value.charAt(i), 10) * (length + 1 - i);
        }
        int digit = (sum * 10) % 11;
        return digit == 10 ? 0 : digit;
    }

    /** RN-21: {@code ***.***.***-34}. */
    public String masked() {
        return "***.***.***-" + value.substring(9);
    }
}
