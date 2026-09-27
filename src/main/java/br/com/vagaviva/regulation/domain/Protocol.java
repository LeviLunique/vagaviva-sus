package br.com.vagaviva.regulation.domain;

import java.util.regex.Pattern;

/** RN-04: protocolo {@code VV-<ano>-<sequencial de 7 dígitos>}, único e imutável. */
public record Protocol(String value) {

    private static final Pattern FORMAT = Pattern.compile("VV-\\d{4}-\\d{7,}");

    public Protocol {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Protocolo fora do formato VV-AAAA-NNNNNNN");
        }
    }

    public static Protocol of(int year, long sequence) {
        return new Protocol("VV-%04d-%07d".formatted(year, sequence));
    }

    /** Aceita digitação do cidadão com espaços nas pontas e letras minúsculas. */
    public static boolean looksValid(String value) {
        return value != null && FORMAT.matcher(value.strip().toUpperCase()).matches();
    }
}
