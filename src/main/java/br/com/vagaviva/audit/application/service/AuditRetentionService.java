package br.com.vagaviva.audit.application.service;

import br.com.vagaviva.audit.application.port.in.PurgeExpiredAuditUseCase;
import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import java.time.Clock;
import java.time.Period;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A trilha é append-only durante o prazo de retenção (5 anos); depois dele, é apagada. */
@Service
class AuditRetentionService implements PurgeExpiredAuditUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    private final AuditEventRepository repository;
    private final Period retention;
    private final Clock clock;

    AuditRetentionService(AuditEventRepository repository,
            @Value("${vagaviva.retention.audit-events}") Period retention, Clock clock) {
        this.repository = repository;
        this.retention = retention;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int purge() {
        int deleted = repository.deleteOlderThan(ZonedDateTime.now(clock).minus(retention).toInstant());
        if (deleted > 0) {
            log.info("Retenção da auditoria: {} evento(s) com mais de {} apagado(s).", deleted, retention);
        }
        return deleted;
    }
}
