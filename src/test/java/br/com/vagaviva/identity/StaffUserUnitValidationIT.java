package br.com.vagaviva.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** RN-03 ponta a ponta: identity consulta o catálogo pela CatalogApi ao vincular a unidade. */
@IntegrationTest
class StaffUserUnitValidationIT {

    @Autowired MockMvcTester mvc;

    @Test
    @DisplayName("REQUESTER com unidade SPECIALIZED ⇒ 422 HEALTH_UNIT_TYPE_MISMATCH; com UBS ⇒ 201")
    void shouldValidateUnitTypeForRole() {
        String admin = login();
        String specialized = createUnit(admin, "SPECIALIZED");
        String primaryCare = createUnit(admin, "PRIMARY_CARE");

        assertThat(createUser(admin, "REQUESTER", specialized))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("HEALTH_UNIT_TYPE_MISMATCH");
        assertThat(createUser(admin, "SCHEDULER", primaryCare))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("HEALTH_UNIT_TYPE_MISMATCH");
        assertThat(createUser(admin, "REQUESTER", primaryCare)).hasStatus(HttpStatus.CREATED);
        assertThat(createUser(admin, "SCHEDULER", specialized)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("unidade inexistente ⇒ 422 HEALTH_UNIT_NOT_FOUND")
    void shouldRejectUnknownUnit() {
        assertThat(createUser(login(), "REQUESTER", UUID.randomUUID().toString()))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("HEALTH_UNIT_NOT_FOUND");
    }

    private MvcTestResult createUser(String token, String role, String unitId) {
        return mvc.post().uri("/api/v1/users").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Profissional","email":"rn03-%s@vagaviva.test","password":"Senha2026Forte","role":"%s","healthUnitId":"%s"}"""
                        .formatted(UUID.randomUUID(), role, unitId))
                .exchange();
    }

    private String createUnit(String token, String type) {
        String cnes = String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999));
        MvcTestResult result = mvc.post().uri("/api/v1/health-units").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cnes":"%s","name":"Unidade %s","type":"%s","municipalityCode":"3550308",
                         "municipalityName":"São Paulo","address":"Rua 1"}""".formatted(cnes, cnes, type))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private String login() {
        MvcTestResult result = mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@vagaviva.test\",\"password\":\"Admin@Test2026\"}").exchange();
        assertThat(result).hasStatusOk();
        return read(result, "$.accessToken");
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }
}
