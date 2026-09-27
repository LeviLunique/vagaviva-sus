package br.com.vagaviva.audit.application.service;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditTrail;
import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import br.com.vagaviva.audit.domain.AuditEvent;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecordAuditService implements AuditTrail {

    private final AuditEventRepository repository;
    private final Clock clock;

    RecordAuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        repository.append(AuditEvent.occurred(entry.actorId(), entry.actorRole(), entry.action(),
                entry.resourceType(), entry.resourceId(), entry.outcome(), entry.clientIp(), entry.details(), clock));
    }
}
