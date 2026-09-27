package br.com.vagaviva.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.support.IntegrationTest;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** RF-26: o paciente age pelo link recebido — sem login, com auditoria e política de liberação. */
@IntegrationTest
@Import({SchedulingTestData.class, ActiveConfirmationTestData.class})
class PatientActionsIT {

    @Autowired MockMvcTester mvc;
    @Autowired ActiveConfirmationTestData data;
    @Autowired JdbcTemplate jdbc;

    private record Scheduled(Referral referral, UUID appointment, String token) {
    }

    private Scheduled scheduled() {
        Referral referral = data.waitingReferral();
        data.publishSlot(referral, 9);
        UUID appointment = data.awaitPendingAppointment(referral.id());
        return new Scheduled(referral, appointment, data.awaitDeliveredToken(appointment));
    }

    private String action(String token, String action) {
        return "/api/v1/patient-actions/" + token + "/" + action;
    }

    @Test
    @DisplayName("link curto ⇒ ver o agendamento ⇒ confirmar (repetir é idempotente) e auditar sem ator")
    void shouldViewAndConfirm() {
        Scheduled s = scheduled();

        assertThat(mvc.get().uri("/p/{t}", s.token())).hasStatus(HttpStatus.FOUND);
        var view = mvc.get().uri("/api/v1/patient-actions/{t}", s.token()).exchange();
        assertThat(view).hasStatusOk();
        assertThat(view).bodyJson().extractingPath("$.firstName").isEqualTo("Joana");
        assertThat(view).bodyJson().extractingPath("$.label").isEqualTo("consulta");
        assertThat(view).bodyJson().extractingPath("$.allowedActions").asArray().contains("CONFIRM", "CANCEL", "WITHDRAW");

        assertThat(mvc.post().uri(action(s.token(), "confirm"))).hasStatusOk().bodyJson()
                .extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(mvc.post().uri(action(s.token(), "confirm"))).hasStatusOk().bodyJson()
                .extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PATIENT_CONFIRMED' "
                + "and resource_id = ? and actor_id is null", Long.class, s.appointment().toString())).isEqualTo(2);
    }

    @Test
    @DisplayName("\"não posso ir\" ⇒ volta à fila com a mesma entrada e a vaga (9 dias) volta a AVAILABLE")
    void shouldCancelAndKeepQueuePosition() {
        Scheduled s = scheduled();
        Timestamp entry = jdbc.queryForObject("select queue_entered_at from referral where id = ?", Timestamp.class,
                s.referral().id());

        assertThat(mvc.post().uri(action(s.token(), "cancel"))).hasStatusOk().bodyJson()
                .extractingPath("$.backToQueue").isEqualTo(true);

        assertThat(jdbc.queryForMap("select status, queue_entered_at, missed_confirmations from referral where id = ?",
                s.referral().id())).containsEntry("status", "WAITING").containsEntry("queue_entered_at", entry)
                .containsEntry("missed_confirmations", 0);
        assertThat(jdbc.queryForMap("select s.status, s.release_count from slot s join appointment a on a.slot_id = s.id "
                + "where a.id = ?", s.appointment())).containsEntry("status", "AVAILABLE").containsEntry("release_count", 1);
        assertThat(mvc.post().uri(action(s.token(), "confirm"))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("\"não preciso mais\" ⇒ sai da fila; link expirado ⇒ 410; link desconhecido ⇒ 404")
    void shouldWithdrawAndRejectBadLinks() {
        Scheduled s = scheduled();

        assertThat(mvc.post().uri(action(s.token(), "withdraw"))).hasStatusOk().bodyJson()
                .extractingPath("$.status").isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select status from referral where id = ?", String.class, s.referral().id()))
                .isEqualTo("WITHDRAWN");

        jdbc.update("update patient_action_token set expires_at = now() - interval '1 minute' where token_hash = ?",
                PatientActionToken.hash(s.token()));
        assertThat(mvc.get().uri("/api/v1/patient-actions/{t}", s.token())).hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("PATIENT_LINK_EXPIRED");
        assertThat(mvc.get().uri("/api/v1/patient-actions/{t}", "A".repeat(22))).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("PATIENT_LINK_NOT_FOUND");
    }
}
