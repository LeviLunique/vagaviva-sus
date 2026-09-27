package br.com.vagaviva.catalog.domain;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.util.regex.Pattern;

/** Código do Cadastro Nacional de Estabelecimentos de Saúde: 7 dígitos. */
public record Cnes(String value) {

    private static final Pattern FORMAT = Pattern.compile("\\d{7}");

    public Cnes {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new BusinessRuleException("INVALID_CNES", "CNES inválido: informe 7 dígitos.");
        }
    }

    public static Cnes of(String value) {
        return new Cnes(value == null ? null : value.strip());
    }
}
