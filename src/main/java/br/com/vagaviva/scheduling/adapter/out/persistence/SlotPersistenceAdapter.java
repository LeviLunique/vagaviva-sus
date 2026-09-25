package br.com.vagaviva.scheduling.adapter.out.persistence;

import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotFilter;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SlotPersistenceAdapter implements SlotRepository {

    private static final String PROFESSIONAL_INDEX = "ux_slot_professional_start";
    private static final Sort BY_START = Sort.by("startAt", "id");

    private final SlotJpaRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;

    SlotPersistenceAdapter(SlotJpaRepository repository, NamedParameterJdbcTemplate jdbc, EntityManager entityManager) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public List<Slot> saveAll(List<Slot> slots) {
        try {
            List<SlotEntity> saved = repository.saveAllAndFlush(slots.stream().map(SlotEntity::from).toList());
            return saved.stream().map(SlotEntity::toDomain).toList();
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    @Override
    public Slot save(Slot slot) {
        try {
            return repository.saveAndFlush(SlotEntity.from(slot)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    @Override
    public Optional<Slot> findById(UUID id) {
        return repository.findById(id).map(SlotEntity::toDomain);
    }

    @Override
    public boolean existsOverlap(UUID unitId, String professionalName, Instant start, Instant end) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1 FROM slot
                   WHERE unit_id = :unitId AND lower(professional_name) = lower(:professional)
                     AND status <> 'CANCELLED'
                     AND start_at < :end AND start_at + make_interval(mins => duration_minutes) > :start)""",
                new MapSqlParameterSource("unitId", unitId).addValue("professional", professionalName)
                        .addValue("start", Timestamp.from(start)).addValue("end", Timestamp.from(end)),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<UUID> findAllocatableIds(Instant from, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM slot WHERE status = 'AVAILABLE' AND start_at >= :from
                 ORDER BY start_at, id LIMIT :limit""",
                new MapSqlParameterSource("from", Timestamp.from(from)).addValue("limit", limit), UUID.class);
    }

    /**
     * A trava é feita em SQL; a entidade é lida em seguida pelo JPA na mesma transação — o
     * {@code clear} garante que o estado venha do banco (e não de um cache anterior à trava).
     */
    @Override
    public Optional<Slot> lockAvailable(UUID id) {
        List<UUID> locked = jdbc.queryForList(
                "SELECT id FROM slot WHERE id = :id AND status = 'AVAILABLE' FOR UPDATE SKIP LOCKED",
                new MapSqlParameterSource("id", id), UUID.class);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        entityManager.clear();
        return repository.findById(id).map(SlotEntity::toDomain);
    }

    @Override
    public Page<Slot> search(SlotFilter filter, Pageable pageable) {
        Pageable byStart = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_START);
        return repository.findAll(matching(filter), byStart).map(SlotEntity::toDomain);
    }

    private static Specification<SlotEntity> matching(SlotFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.unitId() != null) {
                predicates.add(builder.equal(root.get("unitId"), filter.unitId()));
            }
            if (filter.specialtyId() != null) {
                predicates.add(builder.equal(root.get("specialtyId"), filter.specialtyId()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThan(root.get("startAt"), filter.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static RuntimeException translate(DataIntegrityViolationException ex) {
        if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(PROFESSIONAL_INDEX)) {
            return new BusinessRuleException("SLOT_OVERLAP", "O profissional já tem vaga nesse horário.");
        }
        return ex;
    }
}
