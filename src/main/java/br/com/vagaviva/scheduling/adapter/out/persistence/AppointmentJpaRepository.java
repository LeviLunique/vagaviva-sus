package br.com.vagaviva.scheduling.adapter.out.persistence;

import br.com.vagaviva.scheduling.AppointmentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, UUID>,
        JpaSpecificationExecutor<AppointmentEntity> {

    Optional<AppointmentEntity> findFirstBySlotIdAndStatusIn(UUID slotId, Collection<AppointmentStatus> statuses);

    List<AppointmentEntity> findByStatusAndConfirmationDeadlineGreaterThanAndConfirmationDeadlineLessThanEqual(
            AppointmentStatus status, Instant from, Instant to);

    List<AppointmentEntity> findByStatusAndStartAtGreaterThanAndStartAtLessThanEqual(AppointmentStatus status,
            Instant from, Instant to);
}
