package br.com.vagaviva.scheduling.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SlotJpaRepository extends JpaRepository<SlotEntity, UUID>, JpaSpecificationExecutor<SlotEntity> {
}
