package br.com.vagaviva.identity.adapter.in.web;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.aStaffUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase;
import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase.AuthenticationResult;
import br.com.vagaviva.identity.application.port.in.GetStaffUserUseCase;
import br.com.vagaviva.identity.application.port.out.IssuedToken;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.UnauthenticatedException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AuthController.class)
@Import(WebSliceSecurity.class)
class AuthControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AuthenticateUseCase authenticate;
    @MockitoBean GetStaffUserUseCase getStaffUser;

    @Test
    @DisplayName("login é público e devolve token Bearer com validade em segundos e o usuário, sem o hash da senha")
    void shouldLoginWithoutToken() {
        StaffUser user = aStaffUser().build();
        when(authenticate.authenticate(any())).thenReturn(
                new AuthenticationResult(new IssuedToken("jwt-token", Duration.ofMinutes(60)), user));

        var result = mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"regina@vagaviva.test\",\"password\":\"Senha12345\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.accessToken").isEqualTo("jwt-token");
        assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(3600);
        assertThat(result).bodyJson().extractingPath("$.user.role").isEqualTo("REGULATOR");
        assertThat(result).bodyText().doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("credenciais inválidas ⇒ 401 genérico INVALID_CREDENTIALS")
    void shouldReturnGenericUnauthorized() {
        when(authenticate.authenticate(any()))
                .thenThrow(new UnauthenticatedException("INVALID_CREDENTIALS", "E-mail ou senha inválidos."));

        var result = mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@vagaviva.test\",\"password\":\"qualquer123\"}");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("login sem e-mail válido ⇒ 422 com o campo em errors")
    void shouldValidateLoginRequest() {
        var result = mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nao-e-email\",\"password\":\"\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.errors").asArray().hasSize(2);
    }

    @Test
    @DisplayName("/auth/me devolve o perfil do dono do token")
    void shouldReturnCurrentUser() {
        StaffUser user = aStaffUser().build();
        when(getStaffUser.get(user.id())).thenReturn(user);

        var result = mvc.get().uri("/api/v1/auth/me").with(TestJwt.as(Role.REGULATOR, user.id()));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(user.email());
    }

    @Test
    @DisplayName("/auth/me sem token ⇒ 401 em problem+json com WWW-Authenticate")
    void shouldRequireTokenForMe() {
        var result = mvc.get().uri("/api/v1/auth/me");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader("WWW-Authenticate", "Bearer");
    }

    @Test
    @DisplayName("token malformado ⇒ 401")
    void shouldRejectInvalidToken() {
        var result = mvc.get().uri("/api/v1/auth/me").header("Authorization", "Bearer nao.e.jwt");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }
}
