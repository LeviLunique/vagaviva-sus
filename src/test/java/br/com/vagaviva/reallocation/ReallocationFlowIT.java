package br.com.vagaviva.reallocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Fluxo completo com mensagens reais (outbox ⇒ SQS ⇒ sandbox): a vaga de D+3 aceita por A é liberada
 * quando A não pode ir ⇒ nova campanha ⇒ B aceita pelo link ⇒ agendamento {@code SHORT_NOTICE_OFFER}.
 */
@IntegrationTest
@Import({SchedulingTestData.class, ReallocationTestData.class})
class ReallocationFlowIT {

    @Autowired MockMvcTester mvc;
    @Autowired ReallocationTestData data;
    @Autowired SchedulingApi scheduling;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("A aceita encaixe D+3 pelo link, cancela ⇒ oferta para B ⇒ B aceita pelo link; A mantém a posição")
    void releasedShortNoticeSlotGoesToNextPatient() {
        UUID specialty = data.specialty();
        Referral patientA = data.shortNoticeReferral(specialty, 50);
        UUID slot = data.publishSlot(specialty, 72);
        UUID offerA = data.awaitPendingOffers(slot, 1, 1).getFirst();
        String tokenA = data.awaitOfferToken(offerA);

        var viewA = mvc.get().uri("/api/v1/patient-actions/{t}", tokenA).exchange();
        assertThat(viewA).hasStatusOk();
        assertThat(viewA).bodyJson().extractingPath("$.kind").isEqualTo("OFFER");
        assertThat(viewA).bodyJson().extractingPath("$.allowedActions").asArray()
                .containsExactly("ACCEPT_OFFER", "DECLINE_OFFER");
        var acceptA = mvc.post().uri("/api/v1/patient-actions/{t}/accept-offer", tokenA).exchange();
        assertThat(acceptA).hasStatusOk();
        assertThat(acceptA).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        UUID appointmentA = jdbc.queryForObject("select id from appointment where referral_id = ?", UUID.class,
                patientA.id());
        Timestamp entryA = jdbc.queryForObject("select queue_entered_at from referral where id = ?", Timestamp.class,
                patientA.id());

        Referral patientB = data.shortNoticeReferral(specialty, 5);
        scheduling.cancelByPatient(appointmentA);

        UUID offerB = data.awaitPendingOffers(slot, 2, 1).getFirst();
        assertThat(jdbc.queryForObject("select referral_id from slot_offer where id = ?", UUID.class, offerB))
                .as("A já recebeu oferta desta vaga: fica de fora").isEqualTo(patientB.id());
        String tokenB = data.awaitOfferToken(offerB);
        var acceptB = mvc.post().uri("/api/v1/patient-actions/{t}/accept-offer", tokenB).exchange();
        assertThat(acceptB).hasStatusOk();
        assertThat(acceptB).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(mvc.post().uri("/api/v1/patient-actions/{t}/accept-offer", tokenA))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("OFFER_NOT_PENDING");

        Map<String, Object> appointmentB = jdbc.queryForMap(
                "select origin, status from appointment where referral_id = ?", patientB.id());
        assertThat(appointmentB).containsEntry("origin", "SHORT_NOTICE_OFFER").containsEntry("status", "CONFIRMED");
        assertThat(jdbc.queryForMap("select status, queue_entered_at from referral where id = ?", patientA.id()))
                .containsEntry("status", "WAITING").containsEntry("queue_entered_at", entryA);
        assertThat(jdbc.queryForMap("select status, release_count from slot where id = ?", slot))
                .containsEntry("status", "ALLOCATED").containsEntry("release_count", 1);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from notification where appointment_id = ? and type = 'APPOINTMENT_SCHEDULED'",
                Long.class, appointmentA)).as("A recebeu o link do encaixe para liberar a vaga").isEqualTo(1));
    }
}
