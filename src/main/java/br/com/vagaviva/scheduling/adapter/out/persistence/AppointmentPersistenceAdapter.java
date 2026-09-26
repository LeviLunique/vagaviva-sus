package br.com.vagaviva.scheduling.adapter.out.persistence;

import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import jakarta.persistence.criteria.Predicate;
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
import org.springframework.stereotype.Component;

@Component
class AppointmentPersistenceAdapter implements AppointmentRepository {

    private static final Sort BY_START = Sort.by("startAt", "id");

    private final AppointmentJpaRepository repository;
    private final Clock clock;

    AppointmentPersistenceAdapter(AppointmentJpaRepository repository, Clock clock) {
        this.repository = repository;
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
