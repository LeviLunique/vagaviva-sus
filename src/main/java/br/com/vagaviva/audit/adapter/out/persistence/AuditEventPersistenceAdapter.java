package br.com.vagaviva.audit.adapter.out.persistence;

import br.com.vagaviva.audit.application.port.in.AuditEventQuery;
import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import br.com.vagaviva.audit.domain.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class AuditEventPersistenceAdapter implements AuditEventRepository {

    private static final String OCCURRED_AT = "occurredAt";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, OCCURRED_AT);

    private final AuditEventJpaRepository repository;

    AuditEventPersistenceAdapter(AuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AuditEvent event) {
        repository.save(AuditEventEntity.from(event));
    }

    @Override
    public Page<AuditEvent> search(AuditEventQuery query, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return repository.findAll(matching(query), newestFirst).map(AuditEventEntity::toDomain);
    }

    private static Specification<AuditEventEntity> matching(AuditEventQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.resourceType() != null) {
                predicates.add(builder.equal(root.get("resourceType"), query.resourceType()));
            }
            if (query.resourceId() != null) {
                predicates.add(builder.equal(root.get("resourceId"), query.resourceId()));
            }
            if (query.actorId() != null) {
                predicates.add(builder.equal(root.get("actorId"), query.actorId()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get(OCCURRED_AT), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.get(OCCURRED_AT), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
