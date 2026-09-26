package br.com.vagaviva.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.audit.application.port.in.PurgeExpiredAuditUseCase;
import br.com.vagaviva.engagement.application.port.in.PurgeNotificationBodiesUseCase;
import br.com.vagaviva.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** SPEC §10 (LGPD): retenção de 5 anos da auditoria e de 90 dias do texto das mensagens, no banco. */
@IntegrationTest
class RetentionIT {

    @Autowired PurgeExpiredAuditUseCase auditRetention;
    @Autowired PurgeNotificationBodiesUseCase notificationRetention;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private UUID audit(Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into audit_event (id, occurred_at, action, resource_type, outcome) values (?, ?, 'RETENTION_TEST', 'TEST', 'SUCCESS')",
                id, Timestamp.from(at));
        return id;
    }

    private UUID notification(Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into notification (id, patient_id, type, channel, status, destination_hash, body, attempts, created_at)
                values (?, ?, 'REFERRAL_QUEUED', 'SANDBOX', 'SENT', ?, 'VagaViva SUS: Maria, seu pedido entrou na fila.', 1, ?)""",
                id, UUID.randomUUID(), "a".repeat(64), Timestamp.from(at));
        return id;
    }

    @Test
    @DisplayName("apaga auditoria com mais de 5 anos e o texto das mensagens com mais de 90 dias; mantém o resto")
    void purgesOnlyExpiredData() {
        Instant now = clock.instant();
        UUID oldAudit = audit(now.minus(Duration.ofDays(5 * 366)));
        UUID recentAudit = audit(now.minus(Duration.ofDays(4 * 365)));
        UUID oldMessage = notification(now.minus(Duration.ofDays(91)));
        UUID recentMessage = notification(now.minus(Duration.ofDays(89)));

        assertThat(auditRetention.purge()).isGreaterThanOrEqualTo(1);
        assertThat(notificationRetention.purge()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from audit_event where id in (?, ?)", Long.class, oldAudit,
                recentAudit)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where id = ?", Long.class, recentAudit)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select body from notification where id = ?", String.class, oldMessage))
                .isEqualTo("[removido por retenção]");
        assertThat(jdbc.queryForObject("select body from notification where id = ?", String.class, recentMessage))
                .contains("Maria");
        assertThat(jdbc.queryForObject("select status from notification where id = ?", String.class, oldMessage))
                .as("o registro de envio permanece").isEqualTo("SENT");
    }
}
