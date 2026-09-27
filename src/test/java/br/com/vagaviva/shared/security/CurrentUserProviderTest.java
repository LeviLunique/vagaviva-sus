package br.com.vagaviva.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.shared.domain.UnauthenticatedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("extrai id, papel, unidade e nome das claims do JWT")
    void shouldReadUserFromJwt() {
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        authenticate(jwt(userId, "SCHEDULER", unitId.toString()));

        CurrentUser user = provider.get();

        assertThat(user).isEqualTo(new CurrentUser(userId, Role.SCHEDULER, unitId, "Ana Agendadora"));
    }

    @Test
    @DisplayName("usuário sem unidade (ex.: REGULATOR) fica com unitId nulo")
    void shouldAllowUserWithoutUnit() {
        authenticate(jwt(UUID.randomUUID(), "REGULATOR", null));

        assertThat(provider.get().unitId()).isNull();
    }

    @Test
    @DisplayName("sem autenticação JWT ⇒ UnauthenticatedException")
    void shouldRejectMissingAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y"));

        assertThatThrownBy(provider::get).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    @DisplayName("apenas REQUESTER e SCHEDULER exigem unidade de saúde")
    void shouldTellWhichRolesRequireHealthUnit() {
        assertThat(Role.REQUESTER.requiresHealthUnit()).isTrue();
        assertThat(Role.SCHEDULER.requiresHealthUnit()).isTrue();
        assertThat(Role.ADMIN.requiresHealthUnit()).isFalse();
        assertThat(Role.REGULATOR.requiresHealthUnit()).isFalse();
        assertThat(Role.MANAGER.requiresHealthUnit()).isFalse();
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static Jwt jwt(UUID subject, String role, String unitId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.NAME, "Ana Agendadora")
                .issuedAt(Instant.now());
        if (unitId != null) {
            builder.claim(JwtClaims.UNIT_ID, unitId);
        }
        return builder.build();
    }
}
