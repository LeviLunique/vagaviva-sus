package br.com.vagaviva.regulation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import br.com.vagaviva.regulation.events.ReferralQueued;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import br.com.vagaviva.support.TestDocuments;
import br.com.vagaviva.support.TestJwt;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Encaminhar → regular → fila → consulta pública, pela API e com banco real (RF-12 a RF-18). */
@IntegrationTest
@Import(RegulationFlowIT.EventsCaptor.class)
class RegulationFlowIT {

    private static final UUID UBS = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final LocalDate ELDERLY_BIRTH = LocalDate.of(1950, 5, 10);

    @Autowired MockMvcTester mvc;
    @Autowired PatientRepository patients;
    @Autowired SpecialtyRepository specialties;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventsCaptor events;
    @Autowired Clock clock;

    @Test
    @DisplayName("fluxo completo: duplicidade 409, devolução e reenvio, fila na ordem de risco e posição pública")
    void shouldRegulateAndExposeQueue() {
        UUID specialty = newSpecialty(false);
        UUID elderly = newPatient(ELDERLY_BIRTH);
        UUID young = newPatient(LocalDate.of(1995, 3, 3));

        String elderlyReferral = createReferral(elderly, specialty);
        assertThat(post("/api/v1/referrals", Role.REQUESTER, body(young, specialty))).hasStatus(HttpStatus.CREATED);
        String youngReferral = idOfOnly(young, specialty);
        assertThat(post("/api/v1/referrals", Role.REQUESTER, body(elderly, specialty)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("REFERRAL_DUPLICATED");

        assertThat(regulate(youngReferral, "{\"decision\":\"RETURN\",\"justification\":\"Anexar exames.\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("RETURNED");
        assertThat(mvc.put().uri("/api/v1/referrals/{id}", youngReferral).with(TestJwt.as(Role.REQUESTER, REQUESTER, UBS))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clinicalJustification\":\"Exames anexados.\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PENDING_REGULATION");

        assertThat(regulate(elderlyReferral, "{\"decision\":\"APPROVE\",\"riskClass\":\"GREEN\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.priorityGroup").isEqualTo(true);
        assertThat(regulate(youngReferral, "{\"decision\":\"APPROVE\",\"riskClass\":\"RED\"}")).hasStatusOk();
        assertThat(regulate(youngReferral, "{\"decision\":\"APPROVE\",\"riskClass\":\"RED\"}"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("REFERRAL_INVALID_STATE");
        assertThat(events.queued).extracting(ReferralQueued::referralId)
                .contains(UUID.fromString(elderlyReferral), UUID.fromString(youngReferral));

        MvcTestResult queue = mvc.get().uri("/api/v1/queues/{id}", specialty).with(TestJwt.as(Role.MANAGER)).exchange();
        assertThat(queue).hasStatusOk();
        assertThat(queue).bodyJson().extractingPath("$.content[*].riskClass").asArray().containsExactly("RED", "GREEN");
        assertThat(queue).bodyJson().extractingPath("$.content[0].patientCnsMasked").asString().startsWith("***********");

        assertThat(mvc.post().uri("/api/v1/queues/snapshot").with(TestJwt.as(Role.ADMIN))).hasStatusOk();
        String protocol = read(mvc.get().uri("/api/v1/referrals/{id}", elderlyReferral).with(TestJwt.as(Role.REGULATOR))
                .exchange(), "$.protocol");

        MvcTestResult position = publicPosition(protocol, ELDERLY_BIRTH);
        assertThat(position).hasStatusOk();
        assertThat(position).bodyJson().extractingPath("$.position").isEqualTo(2);
        assertThat(position).bodyJson().extractingPath("$.totalInQueue").isEqualTo(2);
        assertThat(position).bodyJson().extractingPath("$.status").isEqualTo("WAITING");
        assertThat(position).bodyText().doesNotContain("Paciente").doesNotContain("cns");

        assertThat(mvc.get().uri("/api/v1/public/queue-stats")).hasStatusOk();
    }

    @Test
    @DisplayName("RF-17: data de nascimento errada e protocolo inexistente dão a mesma resposta (sem vazamento)")
    void shouldNotLeakWhichDataIsWrong() {
        UUID specialty = newSpecialty(true);
        UUID patient = newPatient(ELDERLY_BIRTH);
        String referral = createReferral(patient, specialty);
        String protocol = read(mvc.get().uri("/api/v1/referrals/{id}", referral).with(TestJwt.as(Role.ADMIN)).exchange(),
                "$.protocol");

        MvcTestResult ok = publicPosition(protocol, ELDERLY_BIRTH);
        MvcTestResult wrongBirth = publicPosition(protocol, ELDERLY_BIRTH.plusDays(1));
        MvcTestResult unknown = publicPosition("VV-2099-9999999", ELDERLY_BIRTH);

        assertThat(ok).hasStatusOk().bodyJson().extractingPath("$.specialty").isEqualTo("Consulta especializada");
        assertThat(ok).bodyJson().extractingPath("$.position").isNull();
        assertThat(wrongBirth).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(detail(wrongBirth)).isEqualTo(detail(unknown));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PUBLIC_QUEUE_POSITION_READ' "
                + "and outcome = 'FAILURE' and resource_id is null", Long.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("RN-03: REQUESTER de outra UBS não lê nem reenvia o encaminhamento (403)")
    void shouldScopeRequesterToOwnUnit() {
        String referral = createReferral(newPatient(ELDERLY_BIRTH), newSpecialty(false));
        var otherUnit = TestJwt.as(Role.REQUESTER, UUID.randomUUID(), UUID.randomUUID());

        assertThat(mvc.get().uri("/api/v1/referrals/{id}", referral).with(otherUnit))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("REFERRAL_OUT_OF_UNIT");
        assertThat(mvc.get().uri("/api/v1/referrals").with(otherUnit))
                .hasStatusOk().bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    }

    private String createReferral(UUID patient, UUID specialty) {
        MvcTestResult created = post("/api/v1/referrals", Role.REQUESTER, body(patient, specialty));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.protocol").asString().matches("VV-\\d{4}-\\d{7,}");
        return read(created, "$.id");
    }

    private String idOfOnly(UUID patient, UUID specialty) {
        return jdbc.queryForObject("select id::text from referral where patient_id = ? and specialty_id = ?",
                String.class, patient, specialty);
    }

    private MvcTestResult regulate(String referral, String json) {
        return mvc.post().uri("/api/v1/referrals/{id}/regulation", referral).with(TestJwt.as(Role.REGULATOR))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult post(String uri, Role role, String json) {
        return mvc.post().uri(uri).with(TestJwt.as(role, REQUESTER, UBS)).contentType(MediaType.APPLICATION_JSON)
                .content(json).exchange();
    }

    private MvcTestResult publicPosition(String protocol, LocalDate birth) {
        return mvc.post().uri("/api/v1/public/queue-position").contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocol\":\"%s\",\"birthDate\":\"%s\"}".formatted(protocol, birth)).exchange();
    }

    private static String body(UUID patient, UUID specialty) {
        return """
                {"patientId":"%s","specialtyId":"%s","clinicalJustification":"Avaliação especializada.","acceptsShortNotice":true}"""
                .formatted(patient, specialty);
    }

    private UUID newSpecialty(boolean sensitive) {
        String code = "RG" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        return specialties.save(Specialty.register(code, "Especialidade " + code, SpecialtyType.CONSULTATION, sensitive,
                clock)).id();
    }

    private UUID newPatient(LocalDate birth) {
        return patients.save(Patient.register(Patient.builder()
                .cns(Cns.of(TestDocuments.randomCns()))
                .fullName("Paciente Fictício")
                .birthDate(birth)
                .municipalityCode(MunicipalityCode.of("3550308"))
                .phone(PhoneNumber.of("+5511999990123"))
                .preferredChannel(ContactChannel.SMS), clock)).id();
    }

    private static String detail(MvcTestResult result) {
        return read(result, "$.detail");
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    @TestConfiguration
    static class EventsCaptor {

        final List<ReferralQueued> queued = new CopyOnWriteArrayList<>();

        @EventListener
        void on(ReferralQueued event) {
            queued.add(event);
        }
    }
}
