package br.com.vagaviva.patient.domain;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.util.regex.Pattern;

/**
 * Cartão Nacional de Saúde: 15 dígitos. Definitivos começam com 1 ou 2; provisórios com 7, 8
 * ou 9. Em ambos, a soma dos dígitos com pesos 15..1 é múltipla de 11 (algoritmo do DATASUS).
 */
public record Cns(String value) {

    private static final Pattern FORMAT = Pattern.compile("[12789]\\d{14}");

    public Cns {
        if (!isValid(value)) {
            throw new BusinessRuleException("INVALID_CNS", "CNS inválido: confira os 15 dígitos do cartão.");
        }
    }

    public static Cns of(String value) {
        return new Cns(value == null ? null : value.replaceAll("[\\s.-]", ""));
    }

    static boolean isValid(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            sum += Character.digit(value.charAt(i), 10) * (15 - i);
        }
        return sum % 11 == 0;
    }

    /** RN-21: {@code ***********1234}. */
    public String masked() {
        return "*".repeat(11) + value.substring(11);
    }
}
