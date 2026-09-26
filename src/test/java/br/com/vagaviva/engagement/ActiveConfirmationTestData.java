package br.com.vagaviva.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestDocuments;
import br.com.vagaviva.support.TestJwt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Cenário da confirmação ativa pelos caminhos reais: paciente na fila, agenda publicada pela API,
 * alocação pelo evento e mensagem entregue pelo pipeline (outbox ⇒ SQS ⇒ canal SANDBOX).
 */
@TestComponent
public class ActiveConfirmationTestData {

    private static final AtomicLong PROTOCOLS = new AtomicLong(5_000_000 + System.nanoTime() % 1_000_000);
    private static final Pattern LINK = Pattern.compile("/p/([A-Za-z0-9_-]{22})");

    private final MockMvcTester mvc;
    private final PatientRepository patients;
    private final SpecialtyRepository specialties;
    private final ReferralRepository referrals;
    private final SchedulingTestData scheduling;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    ActiveConfirmationTestData(MockMvcTester mvc, PatientRepository patients, SpecialtyRepository specialties,
            ReferralRepository referrals, SchedulingTestData scheduling, JdbcTemplate jdbc, Clock clock) {
        this.mvc = mvc;
        this.patients = patients;
        this.specialties = specialties;
        this.referrals = referrals;
        this.scheduling = scheduling;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Encaminhamento aguardando na fila de uma especialidade nova, para um paciente fictício novo. */
    public Referral waitingReferral() {
        UUID specialty = specialties.save(Specialty.register("AC" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999),
                "Especialidade confirmação", SpecialtyType.CONSULTATION, true, clock)).id();
        UUID patient = patients.save(Patient.register(Patient.builder()
                .cns(Cns.of(TestDocuments.randomCns()))
                .fullName("Joana Fictícia")
                .birthDate(LocalDate.of(1970, 4, 2))
                .municipalityCode(MunicipalityCode.of("3550308"))
                .phone(PhoneNumber.of("+5511999990456"))
                .preferredChannel(ContactChannel.SMS), clock)).id();
        Instant entry = clock.instant().minus(Duration.ofDays(20)).truncatedTo(ChronoUnit.MICROS);
        Clock entryClock = Clock.fixed(entry, clock.getZone());
        Referral referral = Referral.create(Protocol.of(2097, PROTOCOLS.incrementAndGet()), patient, specialty,
                UUID.randomUUID(), UUID.randomUUID(), "Justificativa", null, false, MunicipalityCode.of("3550308"),
                entryClock);
        referral.approve(RiskClass.YELLOW, false, UUID.randomUUID(), entryClock);
        return referrals.save(referral);
    }

    /** Publica uma vaga daqui a {@code daysAhead} dias pela API; a alocação acontece pelo evento. */
    public UUID publishSlot(Referral referral, int daysAhead) {
        UUID unit = scheduling.specializedUnit(Set.of());
        Instant startAt = clock.instant().plus(Duration.ofDays(daysAhead)).truncatedTo(ChronoUnit.HOURS);
        String body = """
                {"unitId":"%s","specialtyId":"%s","professionalName":"Dra. Confirmação","slots":[{"startAt":"%s","durationMinutes":30}]}"""
                .formatted(unit, referral.specialtyId(), startAt);
        assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), unit))
                .contentType(MediaType.APPLICATION_JSON).content(body)).hasStatus(HttpStatus.CREATED);
        return unit;
    }

    /** Espera o agendamento aguardando confirmação do encaminhamento. */
    public UUID awaitPendingAppointment(UUID referralId) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> jdbc.query(
                "select id from appointment where referral_id = ? and status = 'PENDING_CONFIRMATION'",
                (rs, n) -> rs.getObject(1, UUID.class), referralId).stream().findFirst().orElse(null),
                java.util.Objects::nonNull);
    }

    /** Espera a mensagem de agendamento ser entregue (SENT) e devolve o token do link. */
    public String awaitDeliveredToken(UUID appointmentId) {
        String body = await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.query(
                "select body from notification where appointment_id = ? and type = 'APPOINTMENT_SCHEDULED' and status = 'SENT'",
                (rs, n) -> rs.getString(1), appointmentId).stream().findFirst().orElse(null), java.util.Objects::nonNull);
        Matcher link = LINK.matcher(body);
        assertThat(link.find()).as("link do paciente na mensagem").isTrue();
        return link.group(1);
    }
}
