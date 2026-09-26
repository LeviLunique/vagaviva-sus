package br.com.vagaviva.engagement.adapter.out.persistence;

import br.com.vagaviva.engagement.application.port.in.NotificationQueryUseCases.NotificationFilter;
import br.com.vagaviva.engagement.application.port.out.NotificationRepository;
import br.com.vagaviva.engagement.domain.Notification;
import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
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
class NotificationPersistenceAdapter implements NotificationRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final NotificationJpaRepository repository;

    NotificationPersistenceAdapter(NotificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Notification save(Notification notification) {
        return repository.saveAndFlush(NotificationEntity.from(notification)).toDomain();
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id).map(NotificationEntity::toDomain);
    }

    @Override
    public boolean existsForAppointment(UUID appointmentId, NotificationType type) {
        return repository.existsByAppointmentIdAndType(appointmentId, type);
    }

    @Override
    public boolean existsForReferral(UUID referralId, NotificationType type) {
        return repository.existsByReferralIdAndTypeAndAppointmentIdIsNull(referralId, type);
    }

    @Override
    public Page<Notification> search(NotificationFilter filter, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Specification<NotificationEntity> matching = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.appointmentId() != null) {
                predicates.add(builder.equal(root.get("appointmentId"), filter.appointmentId()));
            }
            if (filter.patientId() != null) {
                predicates.add(builder.equal(root.get("patientId"), filter.patientId()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(matching, newestFirst).map(NotificationEntity::toDomain);
    }

    @Override
    public List<Notification> findSandbox(UUID patientId, int limit) {
        return repository.findByPatientIdAndChannelOrderByCreatedAtDesc(patientId, NotificationChannel.SANDBOX,
                PageRequest.of(0, limit)).stream().map(NotificationEntity::toDomain).toList();
    }
}
