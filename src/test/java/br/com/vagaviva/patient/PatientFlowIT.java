package br.com.vagaviva.patient;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import br.com.vagaviva.support.TestDocuments;
import br.com.vagaviva.support.TestJwt;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Cadastro → busca → leitura → contato, com a trilha de auditoria gravada no banco (RF-05). */
@IntegrationTest
class PatientFlowIT {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("cadastra, busca por CNS (POST), lê mascarado e atualiza contato — cada acesso auditado")
    void shouldRegisterSearchReadAndAudit() {
        UUID requester = UUID.randomUUID();
        String cns = TestDocuments.randomCns();
        MvcTestResult created = mvc.post().uri("/api/v1/patients").with(TestJwt.as(Role.REQUESTER, requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cns":"%s","fullName":"Paciente de Teste","birthDate":"1950-01-01","municipalityCode":"3550308",
                         "phone":"+5511999990099","preferredChannel":"SMS"}""".formatted(cns))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = JsonPath.read(new String(created.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");

        assertThat(mvc.post().uri("/api/v1/patients/search").with(TestJwt.as(Role.REGULATOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cns\":\"" + cns + "\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.priorityGroup").isEqualTo(true);
        assertThat(mvc.get().uri("/api/v1/patients/{id}", id).with(TestJwt.as(Role.ADMIN)))
                .hasStatusOk().bodyJson().extractingPath("$.cnsMasked").isEqualTo("***********" + cns.substring(11));
        assertThat(mvc.patch().uri("/api/v1/patients/{id}/contact", id).with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"preferredChannel\":\"WHATSAPP\",\"whatsappOptIn\":true}"))
                .hasStatusOk().bodyJson().extractingPath("$.preferredChannel").isEqualTo("WHATSAPP");

        assertThat(jdbc.queryForList(
                "select action from audit_event where resource_type = 'PATIENT' and resource_id = ? order by occurred_at",
                String.class, id))
                .containsExactly("PATIENT_CREATED", "PATIENT_READ", "PATIENT_READ", "PATIENT_CONTACT_UPDATED");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where details::text like ?", Long.class,
                "%" + cns + "%")).isZero();
    }

    @Test
    @DisplayName("busca sem resultado ⇒ 404 e a tentativa fica na trilha (a transação não é desfeita)")
    void shouldAuditMissedSearch() {
        UUID regulator = UUID.randomUUID();

        assertThat(mvc.post().uri("/api/v1/patients/search").with(TestJwt.as(Role.REGULATOR, regulator))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cpf\":\"" + TestDocuments.randomCpf() + "\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where actor_id = ? and action = 'PATIENT_SEARCHED' and outcome = 'FAILURE'",
                Long.class, regulator)).isEqualTo(1);
    }
}
