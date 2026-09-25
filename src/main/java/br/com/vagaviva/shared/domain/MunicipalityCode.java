package br.com.vagaviva.shared.domain;

import java.util.regex.Pattern;

/**
 * Código IBGE do município (7 dígitos; o primeiro é a região, 1 a 5). Compartilhado por
 * catálogo (área de atendimento das unidades) e paciente (município de residência).
 */
public record MunicipalityCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[1-5]\\d{6}");

    public MunicipalityCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new BusinessRuleException("INVALID_MUNICIPALITY_CODE",
                    "Código de município inválido: informe o código IBGE de 7 dígitos.");
        }
    }

    public static MunicipalityCode of(String value) {
        return new MunicipalityCode(value == null ? null : value.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
