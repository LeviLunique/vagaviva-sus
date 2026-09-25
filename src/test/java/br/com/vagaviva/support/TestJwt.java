package br.com.vagaviva.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import br.com.vagaviva.shared.security.JwtClaims;
import br.com.vagaviva.shared.security.Role;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Requisição autenticada com um JWT do papel informado (sem precisar de login). */
public final class TestJwt {

    private TestJwt() {
    }

    public static RequestPostProcessor as(Role role) {
        return as(role, UUID.randomUUID());
    }

    public static RequestPostProcessor as(Role role, UUID userId) {
        return jwt()
                .jwt(token -> token.subject(userId.toString())
                        .claim(JwtClaims.ROLE, role.name())
                        .claim(JwtClaims.NAME, "Usuário de teste"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
