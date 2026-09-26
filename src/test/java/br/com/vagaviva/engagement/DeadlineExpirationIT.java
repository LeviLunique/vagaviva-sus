package br.com.vagaviva.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.scheduling.SchedulingTestData;
import br.com.vagaviva.scheduling.application.port.in.ExpireConfirmationsUseCase;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-27/RN-13: sem confirmação no prazo, a vaga é liberada; na 2ª vez o paciente volta à regulação. */
@IntegrationTest
@Import({SchedulingTestData.class, ActiveConfirmationTestData.class})
class DeadlineExpirationIT {

    @Autowired ActiveConfirmationTestData data;
    @Autowired ExpireConfirmationsUseCase expirations;
    @Autowired RunAllocationUseCase allocation;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("1ª expiração ⇒ volta à fila e a vaga é realocada; 2ª expiração ⇒ PENDING_REGULATION")
    void secondExpirationSendsBackToRegulation() {
        Referral referral = data.waitingReferral();
        data.publishSlot(referral, 9);
        UUID first = data.awaitPendingAppointment(referral.id());

        expirations.expireNow(first);

        assertThat(status("appointment", first)).isEqualTo("EXPIRED_UNCONFIRMED");
        assertThat(jdbc.queryForMap("select status, missed_confirmations from referral where id = ?", referral.id()))
                .containsEntry("status", "WAITING").containsEntry("missed_confirmations", 1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'APPOINTMENT_EXPIRED' "
                + "and resource_id = ?", Long.class, first.toString())).isEqualTo(1);

        allocation.run();
        UUID second = data.awaitPendingAppointment(referral.id());
        assertThat(second).isNotEqualTo(first);

        expirations.expireNow(second);

        assertThat(status("referral", referral.id())).isEqualTo("PENDING_REGULATION");
        assertThat(jdbc.queryForObject("select s.release_count from slot s join appointment a on a.slot_id = s.id "
                + "where a.id = ?", Integer.class, second)).isEqualTo(2);
    }

    private String status(String table, UUID id) {
        return jdbc.queryForObject("select status from " + table + " where id = ?", String.class, id);
    }
}
