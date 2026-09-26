package br.com.vagaviva.reallocation;

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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Cenários do encaixe pelos caminhos reais: fila de quem aceita encaixe, vaga publicada pela API. */
@TestComponent
public class ReallocationTestData {

    private static final AtomicLong PROTOCOLS = new AtomicLong(6_000_000 + System.nanoTime() % 1_000_000);
    private static final Pattern LINK = Pattern.compile("/p/([A-Za-z0-9_-]{22})");

    private final MockMvcTester mvc;
    private final PatientRepository patients;
    private final SpecialtyRepository specialties;
    private final ReferralRepository referrals;
    private final SchedulingTestData scheduling;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    ReallocationTestData(MockMvcTester mvc, PatientRepository patients, SpecialtyRepository specialties,
            ReferralRepository referrals, SchedulingTestData scheduling, JdbcTemplate jdbc, Clock clock) {
        this.mvc = mvc;
        this.patients = patients;
        this.specialties = specialties;
        this.referrals = referrals;
        this.scheduling = scheduling;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public UUID specialty() {
        return specialties.save(Specialty.register("RA" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999),
                "Especialidade encaixe", SpecialtyType.CONSULTATION, false, clock)).id();
    }

    /** Encaminhamento na fila de quem aceita encaixe; entrada {@code daysAgo} dias atrás (define a ordem). */
    public Referral shortNoticeReferral(UUID specialty, int daysAgo) {
        UUID patient = patients.save(Patient.register(Patient.builder()
                .cns(Cns.of(TestDocuments.randomCns()))
                .fullName("Carla Fictícia")
                .birthDate(LocalDate.of(1980, 7, 1))
                .municipalityCode(MunicipalityCode.of("3550308"))
                .phone(PhoneNumber.of("+5511999990789"))
                .preferredChannel(ContactChannel.SMS), clock)).id();
        Clock entry = Clock.fixed(clock.instant().minus(Duration.ofDays(daysAgo)).truncatedTo(ChronoUnit.MICROS),
                clock.getZone());
        Referral referral = Referral.create(Protocol.of(2097, PROTOCOLS.incrementAndGet()), patient, specialty,
                UUID.randomUUID(), UUID.randomUUID(), "Justificativa", null, true, MunicipalityCode.of("3550308"), entry);
        referral.approve(RiskClass.YELLOW, false, UUID.randomUUID(), entry);
        return referrals.save(referral);
    }

    /** Publica pela API uma vaga daqui a {@code hoursAhead} horas; com menos de 5 dias ela abre para encaixe. */
    public UUID publishSlot(UUID specialty, long hoursAhead) {
        UUID unit = scheduling.specializedUnit(Set.of());
        Instant startAt = clock.instant().plus(Duration.ofHours(hoursAhead)).truncatedTo(ChronoUnit.HOURS);
        String body = """
                {"unitId":"%s","specialtyId":"%s","professionalName":"Dr. Encaixe","slots":[{"startAt":"%s","durationMinutes":30}]}"""
                .formatted(unit, specialty, startAt);
        MvcTestResult created = mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), unit))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$[0].status").isEqualTo("OPEN_FOR_OFFERS");
        return jdbc.queryForObject("select id from slot where unit_id = ? and specialty_id = ?", UUID.class, unit,
                specialty);
    }

    /** Espera a rodada ter {@code count} ofertas pendentes; devolve os ids na ordem da fila. */
    public List<UUID> awaitPendingOffers(UUID slotId, int round, int count) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> jdbc.queryForList(
                "select id from slot_offer where slot_id = ? and round = ? and status = 'PENDING' order by created_at, id",
                UUID.class, slotId, round), ids -> ids.size() == count);
    }

    public UUID offerOf(UUID referralId) {
        return jdbc.queryForObject("select id from slot_offer where referral_id = ? and status = 'PENDING'", UUID.class,
                referralId);
    }

    public String status(String table, UUID id) {
        return jdbc.queryForObject("select status from " + table + " where id = ?", String.class, id);
    }

    /** Espera a mensagem da oferta ser entregue (SENT) e devolve o token do link. */
    public String awaitOfferToken(UUID offerId) {
        String body = await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.query(
                "select body from notification where offer_id = ? and type = 'SLOT_OFFER' and status = 'SENT'",
                (rs, n) -> rs.getString(1), offerId).stream().findFirst().orElse(null), Objects::nonNull);
        Matcher link = LINK.matcher(body);
        assertThat(link.find()).as("link da oferta na mensagem").isTrue();
        return link.group(1);
    }
}
