package br.com.vagaviva.engagement.adapter.out.persistence;

import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID>,
        JpaSpecificationExecutor<NotificationEntity> {

    boolean existsByAppointmentIdAndType(UUID appointmentId, NotificationType type);

    boolean existsByReferralIdAndTypeAndAppointmentIdIsNull(UUID referralId, NotificationType type);

    List<NotificationEntity> findByPatientIdAndChannelOrderByCreatedAtDesc(UUID patientId, NotificationChannel channel,
            Pageable pageable);
}
