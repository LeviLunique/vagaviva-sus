package br.com.vagaviva.catalog.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface HealthUnitJpaRepository extends JpaRepository<HealthUnitEntity, UUID>,
        JpaSpecificationExecutor<HealthUnitEntity> {

    boolean existsByCnes(String cnes);
}
