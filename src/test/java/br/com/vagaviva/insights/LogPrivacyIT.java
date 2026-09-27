package br.com.vagaviva.insights;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.engagement.ActiveConfirmationTestData;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import br.com.vagaviva.support.TestDocuments;
import br.com.vagaviva.support.TestJwt;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * RF-36/RNF-06: nos fluxos principais — cadastro e busca de paciente,
 * encaminhamento, regulação, alocação, mensagem e link do paciente — nenhum log contém CNS, CPF,
 * telefone, nome completo ou o token do link.
 */
@IntegrationTest
@Import({SchedulingTestData.class, ActiveConfirmationTestData.class})
class LogPrivacyIT {

    private static final UUID UBS = UUID.randomUUID();

    @Autowired MockMvcTester mvc;
    @Autowired SchedulingTestData scheduling;
    @Autowired ActiveConfirmationTestData confirmation;
    @Autowired SpecialtyRepository specialties;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void capture() {
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void release() {
        root.detachAppender(appender);
    }

    private String json(MvcTestResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), path);
    }

    @Test
    @DisplayName("fluxo completo sem PII nos logs (mensagem, argumentos, MDC e exceções)")
    void mainFlowsDoNotLeakPersonalData() throws Exception {
        String cns = TestDocuments.randomCns();
        String cpf = TestDocuments.randomCpf();
        String phone = "+55119" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
        String fullName = "Heloísa Privacidade " + ThreadLocalRandom.current().nextInt(1000, 9999);

        MvcTestResult patient = mvc.post().uri("/api/v1/patients").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"cns":"%s","cpf":"%s","fullName":"%s","birthDate":"1966-02-11","municipalityCode":"3550308",
                         "phone":"%s","preferredChannel":"SMS"}""".formatted(cns, cpf, fullName, phone)).exchange();
        assertThat(patient).hasStatus(HttpStatus.CREATED);
        String patientId = json(patient, "$.id");
        assertThat(mvc.post().uri("/api/v1/patients/search").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cns\":\"%s\"}".formatted(cns))).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/patients/search").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cpf\":\"%s\"}".formatted(TestDocuments.randomCpf())))
                .hasStatus(HttpStatus.NOT_FOUND);

        UUID specialty = specialties.save(Specialty.register("LP" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999),
                "Especialidade privacidade", SpecialtyType.CONSULTATION, true, clock)).id();
        MvcTestResult referral = mvc.post().uri("/api/v1/referrals").with(TestJwt.as(Role.REQUESTER, UUID.randomUUID(), UBS))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"patientId":"%s","specialtyId":"%s","clinicalJustification":"Dor persistente.","acceptsShortNotice":false}"""
                        .formatted(patientId, specialty)).exchange();
        assertThat(referral).hasStatus(HttpStatus.CREATED);
        String referralId = json(referral, "$.id");
        assertThat(mvc.post().uri("/api/v1/referrals/{id}/regulation", referralId).with(TestJwt.as(Role.REGULATOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"riskClass\":\"RED\"}"))
                .hasStatusOk();

        UUID unit = scheduling.specializedUnit(java.util.Set.of());
        String startAt = clock.instant().plus(java.time.Duration.ofDays(9)).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .toString();
        assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), unit))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"unitId":"%s","specialtyId":"%s","professionalName":"Dra. Privacidade","slots":[{"startAt":"%s","durationMinutes":30}]}"""
                        .formatted(unit, specialty, startAt))).hasStatus(HttpStatus.CREATED);
        UUID appointment = confirmation.awaitPendingAppointment(UUID.fromString(referralId));
        String token = confirmation.awaitDeliveredToken(appointment);
        assertThat(mvc.get().uri("/api/v1/patient-actions/{t}", token)).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/patient-actions/{t}/confirm", token)).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/patient-actions/{t}", "B".repeat(22))).hasStatus(HttpStatus.NOT_FOUND);

        List<String> logged = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            logged.add(event.getFormattedMessage());
            logged.add(String.valueOf(event.getMDCPropertyMap()));
            for (IThrowableProxy error = event.getThrowableProxy(); error != null; error = error.getCause()) {
                logged.add(error.getMessage());
            }
        }
        String digitsOfPhone = phone.substring(1);
        assertThat(logged).isNotEmpty().allSatisfy(line -> assertThat(String.valueOf(line))
                .doesNotContain(cns).doesNotContain(cpf).doesNotContain(digitsOfPhone).doesNotContain(fullName)
                .doesNotContain(token));
        assertThat(appender.list).as("RNF-06: logs correlacionados pelo traceId (MDC)")
                .anyMatch(event -> event.getMDCPropertyMap().getOrDefault("traceId", "").matches("[0-9a-f]{32}"));
        assertThat(jdbc.queryForObject("select count(*) from notification where appointment_id = ? and status = 'SENT'",
                Long.class, appointment)).isEqualTo(1);
    }
}
