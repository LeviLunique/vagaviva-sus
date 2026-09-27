package br.com.vagaviva.engagement.adapter.out.persistence;

import br.com.vagaviva.engagement.domain.NotificationChannel;
import br.com.vagaviva.engagement.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID>,
        JpaSpecificationExecutor<NotificationEntity> {

    boolean existsByAppointmentIdAndType(UUID appointmentId, NotificationType type);

    boolean existsByOfferId(UUID offerId);

    @Modifying
    @Query("update NotificationEntity n set n.body = :removed where n.createdAt < :limit and n.body <> :removed")
    int purgeBodies(@Param("limit") Instant limit, @Param("removed") String removed);

    @Query("""
            select n.channel, count(n) from NotificationEntity n
            where n.status = br.com.vagaviva.engagement.domain.NotificationStatus.SENT
              and n.sentAt >= :from and n.sentAt < :to
            group by n.channel""")
    List<Object[]> countSentByChannel(@Param("from") Instant from, @Param("to") Instant to);

    boolean existsByReferralIdAndTypeAndAppointmentIdIsNull(UUID referralId, NotificationType type);

    List<NotificationEntity> findByPatientIdAndChannelOrderByCreatedAtDesc(UUID patientId, NotificationChannel channel,
            Pageable pageable);
}
