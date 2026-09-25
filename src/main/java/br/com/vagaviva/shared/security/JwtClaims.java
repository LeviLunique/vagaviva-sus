package br.com.vagaviva.shared.security;

/** Nomes das claims próprias do VagaViva no token (além de {@code iss}, {@code sub}, {@code iat}, {@code exp}). */
public final class JwtClaims {

    public static final String ROLE = "role";
    public static final String UNIT_ID = "unit_id";
    public static final String NAME = "name";

    private JwtClaims() {
    }
}
