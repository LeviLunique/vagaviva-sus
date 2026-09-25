package br.com.vagaviva.audit.application.port.out;

import br.com.vagaviva.audit.application.port.in.AuditEventQuery;
import br.com.vagaviva.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEventRepository {

    void append(AuditEvent event);

    Page<AuditEvent> search(AuditEventQuery query, Pageable pageable);
}
