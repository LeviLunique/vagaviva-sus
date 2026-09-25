package br.com.vagaviva.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Fluxo ponta a ponta: login → /auth/me → gestão de usuários → auditoria, com JWT e banco reais. */
@IntegrationTest
class AuthFlowIT {

    private static final String ADMIN_EMAIL = "admin@vagaviva.test";
    private static final String ADMIN_PASSWORD = "Admin@Test2026";

    @Autowired MockMvcTester mvc;

    @Test
    @DisplayName("login do ADMIN → /auth/me → cria REGULATOR → REGULATOR em rota de ADMIN recebe 403 → auditoria registra")
    void shouldAuthenticateAndAuthorizeByRole() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(mvc.get().uri("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .hasStatusOk()
                .bodyJson().extractingPath("$.role").isEqualTo("ADMIN");

        String regulatorEmail = "regulador-" + UUID.randomUUID() + "@vagaviva.test";
        MvcTestResult created = createUser(adminToken, regulatorEmail, "REGULATOR");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String regulatorId = read(created, "$.id");

        String regulatorToken = login(regulatorEmail.toUpperCase(), "Senha2026Forte");
        assertThat(mvc.get().uri("/api/v1/users").header("Authorization", "Bearer " + regulatorToken))
                .hasStatus(HttpStatus.FORBIDDEN);

        assertThat(mvc.get().uri("/api/v1/audit-events?resourceId={id}", regulatorId)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatusOk()
                .bodyJson().extractingPath("$.content[*].action")
                .asArray().contains("STAFF_USER_CREATED", "LOGIN_SUCCEEDED");
    }

    @Test
    @DisplayName("e-mail duplicado ⇒ 409; senha fraca ⇒ 422")
    void shouldRejectDuplicateEmailAndWeakPassword() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(createUser(adminToken, ADMIN_EMAIL.toUpperCase(), "MANAGER")).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post().uri("/api/v1/users").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Fraco","email":"fraco-%s@vagaviva.test","password":"123","role":"MANAGER"}"""
                        .formatted(UUID.randomUUID())))
                .hasStatus(422)
                .bodyJson().extractingPath("$.code").isEqualTo("WEAK_PASSWORD");
    }

    @Test
    @DisplayName("RN-01: 5 senhas erradas bloqueiam — a senha certa também passa a receber 401")
    void shouldLockAfterFiveFailures() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = "bloqueio-" + UUID.randomUUID() + "@vagaviva.test";
        assertThat(createUser(adminToken, email, "MANAGER")).hasStatus(HttpStatus.CREATED);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(loginRequest(email, "SenhaErrada1")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        assertThat(loginRequest(email, "Senha2026Forte"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("usuário desativado não faz login; reativado volta a entrar")
    void shouldBlockDeactivatedUser() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = "desativado-" + UUID.randomUUID() + "@vagaviva.test";
        String id = read(createUser(adminToken, email, "MANAGER"), "$.id");

        assertThat(changeStatus(adminToken, id, false)).hasStatusOk();
        assertThat(loginRequest(email, "Senha2026Forte")).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(changeStatus(adminToken, id, true)).hasStatusOk();
        assertThat(loginRequest(email, "Senha2026Forte")).hasStatusOk();
    }

    private MvcTestResult changeStatus(String token, String id, boolean active) {
        return mvc.patch().uri("/api/v1/users/{id}/status", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}").exchange();
    }

    private MvcTestResult createUser(String token, String email, String role) {
        return mvc.post().uri("/api/v1/users").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Profissional de Teste","email":"%s","password":"Senha2026Forte","role":"%s"}"""
                        .formatted(email, role))
                .exchange();
    }

    private MvcTestResult loginRequest(String email, String password) {
        return mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
                .exchange();
    }

    private String login(String email, String password) {
        MvcTestResult result = loginRequest(email, password);
        assertThat(result).hasStatusOk();
        return read(result, "$.accessToken");
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }
}
