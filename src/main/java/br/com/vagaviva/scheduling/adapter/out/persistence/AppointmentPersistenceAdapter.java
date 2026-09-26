package br.com.vagaviva.scheduling.adapter.out.persistence;

import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SchedulingApi.ReminderType;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class AppointmentPersistenceAdapter implements AppointmentRepository {

    private static final Sort BY_START = Sort.by("startAt", "id");

    private final AppointmentJpaRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final Clock clock;

    AppointmentPersistenceAdapter(AppointmentJpaRepository repository, NamedParameterJdbcTemplate jdbc,
            EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public Appointment save(Appointment appointment) {
        return repository.saveAndFlush(AppointmentEntity.from(appointment)).toDomain();
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        return repository.findById(id).map(AppointmentEntity::toDomain);
    }

    @Override
    public Optional<Appointment> findOpenBySlot(UUID slotId) {
        return repository.findFirstBySlotIdAndStatusIn(slotId,
                EnumSet.of(AppointmentStatus.PENDING_CONFIRMATION, AppointmentStatus.CONFIRMED))
                .map(AppointmentEntity::toDomain);
    }

    @Override
    public Page<Appointment> search(AppointmentFilter filter, Pageable pageable) {
        Pageable byStart = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_START);
        return repository.findAll(matching(filter), byStart).map(AppointmentEntity::toDomain);
    }

    @Override
    public List<UUID> findOverdueIds(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM appointment WHERE status = 'PENDING_CONFIRMATION' AND confirmation_deadline < :now
                 ORDER BY confirmation_deadline, id LIMIT :limit""",
                new MapSqlParameterSource("now", Timestamp.from(now)).addValue("limit", limit), UUID.class);
    }

    /** Trava em SQL e relê pelo JPA (o {@code clear} garante o estado do banco, não de cache anterior). */
    @Override
    public Optional<Appointment> lockPendingConfirmation(UUID id) {
        List<UUID> locked = jdbc.queryForList(
                "SELECT id FROM appointment WHERE id = :id AND status = 'PENDING_CONFIRMATION' FOR UPDATE SKIP LOCKED",
                new MapSqlParameterSource("id", id), UUID.class);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        entityManager.clear();
        return repository.findById(id).map(AppointmentEntity::toDomain);
    }

    @Override
    public List<Appointment> findForReminder(ReminderType type, Instant from, Instant to) {
        List<AppointmentEntity> found = type == ReminderType.CONFIRMATION
                ? repository.findByStatusAndConfirmationDeadlineGreaterThanAndConfirmationDeadlineLessThanEqual(
                        AppointmentStatus.PENDING_CONFIRMATION, from, to)
                : repository.findByStatusAndStartAtGreaterThanAndStartAtLessThanEqual(AppointmentStatus.CONFIRMED,
                        from, to);
        return found.stream().map(AppointmentEntity::toDomain).toList();
    }

    /** A data do filtro é uma data civil no fuso de negócio (o do relógio). */
    private Specification<AppointmentEntity> matching(AppointmentFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.unitId() != null) {
                predicates.add(builder.equal(root.get("unitId"), filter.unitId()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.date() != null) {
                Instant dayStart = filter.date().atStartOfDay(clock.getZone()).toInstant();
                Instant nextDay = filter.date().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
                predicates.add(builder.greaterThanOrEqualTo(root.get("startAt"), dayStart));
                predicates.add(builder.lessThan(root.get("startAt"), nextDay));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
