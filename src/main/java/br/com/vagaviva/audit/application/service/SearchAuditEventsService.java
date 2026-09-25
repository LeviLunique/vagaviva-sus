package br.com.vagaviva.audit.application.service;

import br.com.vagaviva.audit.application.port.in.AuditEventQuery;
import br.com.vagaviva.audit.application.port.in.SearchAuditEventsUseCase;
import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import br.com.vagaviva.audit.domain.AuditEvent;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SearchAuditEventsService implements SearchAuditEventsUseCase {

    private final AuditEventRepository repository;

    SearchAuditEventsService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditEventQuery query, Pageable pageable) {
        if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
            throw new BusinessRuleException("INVALID_PERIOD", "O início do período (from) deve ser anterior ao fim (to).");
        }
        return repository.search(query, pageable);
    }
}
