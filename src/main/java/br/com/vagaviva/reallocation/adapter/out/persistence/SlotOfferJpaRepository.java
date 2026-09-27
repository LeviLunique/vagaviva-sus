package br.com.vagaviva.reallocation.adapter.out.persistence;

import br.com.vagaviva.reallocation.OfferStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SlotOfferJpaRepository extends JpaRepository<SlotOfferEntity, UUID>, JpaSpecificationExecutor<SlotOfferEntity> {

    boolean existsBySlotIdAndStatus(UUID slotId, OfferStatus status);

    @Query("select coalesce(max(o.round), 0) from SlotOfferEntity o where o.slotId = :slotId")
    int findLastRound(@Param("slotId") UUID slotId);

    @Query("""
            select distinct o.patientId from SlotOfferEntity o
            where o.slotId = :slotId or o.status = br.com.vagaviva.reallocation.OfferStatus.PENDING""")
    List<UUID> findPatientsToExclude(@Param("slotId") UUID slotId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SlotOfferEntity o set o.status = br.com.vagaviva.reallocation.OfferStatus.SUPERSEDED, o.respondedAt = :now
            where o.slotId = :slotId and o.id <> :acceptedId and o.status = br.com.vagaviva.reallocation.OfferStatus.PENDING""")
    int supersedeOthers(@Param("slotId") UUID slotId, @Param("acceptedId") UUID acceptedId, @Param("now") Instant now);

    @Query("""
            select distinct o.slotId from SlotOfferEntity o
            where o.status = br.com.vagaviva.reallocation.OfferStatus.PENDING and o.expiresAt <= :now""")
    List<UUID> findSlotsWithOverdueOffers(@Param("now") Instant now, Pageable limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SlotOfferEntity o set o.status = br.com.vagaviva.reallocation.OfferStatus.EXPIRED
            where o.slotId = :slotId and o.status = br.com.vagaviva.reallocation.OfferStatus.PENDING
              and o.expiresAt <= :now""")
    int expireOverdue(@Param("slotId") UUID slotId, @Param("now") Instant now);
}
