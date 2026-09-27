package br.com.vagaviva.audit.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID>,
        JpaSpecificationExecutor<AuditEventEntity> {

    @Modifying
    @Query("delete from AuditEventEntity e where e.occurredAt < :limit")
    int deleteByOccurredAtBefore(@Param("limit") Instant limit);
}
