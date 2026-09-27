package br.com.vagaviva.audit.application.port.in;

import br.com.vagaviva.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchAuditEventsUseCase {

    /** Eventos mais recentes primeiro. */
    Page<AuditEvent> search(AuditEventQuery query, Pageable pageable);
}
