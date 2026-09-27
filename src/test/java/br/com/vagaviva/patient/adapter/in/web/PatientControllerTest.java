package br.com.vagaviva.patient.adapter.in.web;

import static br.com.vagaviva.patient.fixtures.PatientFixture.aPatient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.patient.application.port.in.ReadPatientUseCase;
import br.com.vagaviva.patient.application.port.in.RegisterPatientUseCase;
import br.com.vagaviva.patient.application.port.in.UpdatePatientContactUseCase;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.config.ClockConfig;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PatientController.class)
@Import({WebSliceSecurity.class, ClockConfig.class})
class PatientControllerTest {

    private static final String BODY = """
            {"cns":"115881399860000","cpf":"68469788019","fullName":"Maria da Silva","birthDate":"1958-03-14",
             "municipalityCode":"3550308","phone":"+5511999990001","preferredChannel":"WHATSAPP","whatsappOptIn":true,
             "pregnant":false,"disability":false}""";

    @Autowired MockMvcTester mvc;
    @MockitoBean RegisterPatientUseCase registerPatient;
    @MockitoBean ReadPatientUseCase readPatient;
    @MockitoBean UpdatePatientContactUseCase updateContact;

    @Test
    @DisplayName("REQUESTER cadastra paciente ⇒ 201; resposta com CNS e CPF mascarados (RN-21)")
    void shouldRegisterWithMaskedDocuments() {
        Patient patient = aPatient();
        when(registerPatient.register(any())).thenReturn(patient);

        var result = mvc.post().uri("/api/v1/patients").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content(BODY);

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/v1/patients/" + patient.id());
        assertThat(result).bodyJson().extractingPath("$.cnsMasked").isEqualTo("***********0000");
        assertThat(result).bodyJson().extractingPath("$.cpfMasked").isEqualTo("***.***.***-19");
        assertThat(result).bodyText().doesNotContain("115881399860000").doesNotContain("68469788019");
    }

    @Test
    @DisplayName("gestante, PcD e aceite do WhatsApp ausentes no corpo valem false")
    void shouldDefaultOptionalFlagsToFalse() {
        when(registerPatient.register(any())).thenReturn(aPatient());

        assertThat(mvc.post().uri("/api/v1/patients").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cns":"115881399860000","fullName":"Maria da Silva","birthDate":"1958-03-14",
                         "municipalityCode":"3550308","phone":"+5511999990001","preferredChannel":"SMS"}"""))
                .hasStatus(HttpStatus.CREATED);
        verify(registerPatient).register(argThat(command -> !command.whatsappOptIn() && !command.pregnant()
                && !command.disability()));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SCHEDULER", "MANAGER", "REGULATOR"})
    @DisplayName("apenas REQUESTER e ADMIN cadastram paciente")
    void shouldForbidRegistrationForOtherRoles(Role role) {
        assertThat(mvc.post().uri("/api/v1/patients").with(TestJwt.as(role))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(registerPatient);
    }

    @Test
    @DisplayName("campos obrigatórios ausentes ⇒ 422 com errors; documento inválido ⇒ 422 com o código")
    void shouldValidateRegistration() {
        assertThat(mvc.post().uri("/api/v1/patients").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");

        when(registerPatient.register(any())).thenThrow(new BusinessRuleException("INVALID_CNS", "CNS inválido"));
        assertThat(mvc.post().uri("/api/v1/patients").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CNS");
    }

    @Test
    @DisplayName("REGULATOR lê paciente; SCHEDULER não (403)")
    void shouldReadPatientByRole() {
        Patient patient = aPatient();
        UUID regulatorId = UUID.randomUUID();
        when(readPatient.get(eq(patient.id()), argThat(actor -> actor.id().equals(regulatorId)), any())).thenReturn(patient);

        assertThat(mvc.get().uri("/api/v1/patients/{id}", patient.id()).with(TestJwt.as(Role.REGULATOR, regulatorId)))
                .hasStatusOk().bodyJson().extractingPath("$.cnsMasked").isEqualTo("***********0000");
        assertThat(mvc.get().uri("/api/v1/patients/{id}", patient.id()).with(TestJwt.as(Role.SCHEDULER)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RN-22: busca recebe o documento no corpo (POST)")
    void shouldSearchWithBody() {
        Patient patient = aPatient();
        when(readPatient.search(argThat(query -> "115881399860000".equals(query.cns())), any(), any())).thenReturn(patient);

        assertThat(mvc.post().uri("/api/v1/patients/search").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cns\":\"115881399860000\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.id").isEqualTo(patient.id().toString());
    }

    @Test
    @DisplayName("REQUESTER atualiza contato; MANAGER não (403)")
    void shouldUpdateContact() {
        Patient patient = aPatient();
        when(updateContact.update(any())).thenReturn(patient);

        assertThat(mvc.patch().uri("/api/v1/patients/{id}/contact", patient.id()).with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+5511988887777\"}")).hasStatusOk();
        verify(updateContact).update(argThat(command -> "+5511988887777".equals(command.phone())
                && command.preferredChannel() == null && command.patientId().equals(patient.id())));
        assertThat(mvc.patch().uri("/api/v1/patients/{id}/contact", patient.id()).with(TestJwt.as(Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(HttpStatus.FORBIDDEN);
    }
}
