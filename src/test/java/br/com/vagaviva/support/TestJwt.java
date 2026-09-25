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
        return as(role, userId, null);
    }

    /** Profissional vinculado a uma unidade (claim {@code unit_id}), ex.: REQUESTER da UBS. */
    public static RequestPostProcessor as(Role role, UUID userId, UUID unitId) {
        return jwt()
                .jwt(token -> {
                    token.subject(userId.toString())
                            .claim(JwtClaims.ROLE, role.name())
                            .claim(JwtClaims.NAME, "Usuário de teste");
                    if (unitId != null) {
                        token.claim(JwtClaims.UNIT_ID, unitId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
