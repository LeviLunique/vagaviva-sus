package br.com.vagaviva.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.vagaviva.engagement.domain.PatientActionToken;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Evento de domínio ⇒ notificação + outbox ⇒ fila SQS (ElasticMQ) ⇒ consumidor ⇒ canal SANDBOX (RF-25, RN-24). */
@IntegrationTest
@Import({SchedulingTestData.class, ActiveConfirmationTestData.class})
class NotificationPipelineIT {

    @Autowired ActiveConfirmationTestData data;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("agendamento gera mensagem entregue pelo SQS: SENT, 1 tentativa, texto sem especialidade e com link")
    void appointmentMessageTravelsThroughQueue() {
        Referral referral = data.waitingReferral();
        data.publishSlot(referral, 9);
        UUID appointment = data.awaitPendingAppointment(referral.id());

        String token = data.awaitDeliveredToken(appointment);

        Map<String, Object> row = jdbc.queryForMap("select channel, attempts, provider_message_id, destination_hash, body "
                + "from notification where appointment_id = ? and type = 'APPOINTMENT_SCHEDULED'", appointment);
        assertThat(row.get("channel")).isEqualTo("SANDBOX");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat((String) row.get("provider_message_id")).startsWith("sandbox-");
        assertThat(row.get("destination_hash")).isEqualTo(PatientActionToken.hash("+5511999990456"));
        assertThat((String) row.get("body")).contains("Joana").contains("consulta")
                .doesNotContain("Especialidade").doesNotContain("+5511");
        assertThat(jdbc.queryForObject("select count(*) from patient_action_token where subject_id = ? and token_hash = ?",
                Long.class, appointment, PatientActionToken.hash(token))).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from event_publication where event_type like '%NotificationDispatchRequested' "
                        + "and completion_date is null", Long.class)).isZero());
    }
}
