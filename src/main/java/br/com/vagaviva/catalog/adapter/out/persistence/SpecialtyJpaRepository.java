package br.com.vagaviva.catalog.adapter.out.persistence;

import br.com.vagaviva.catalog.SpecialtyType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpecialtyJpaRepository extends JpaRepository<SpecialtyEntity, UUID> {

    boolean existsByCode(String code);

    List<SpecialtyEntity> findByType(SpecialtyType type, Sort sort);
}
