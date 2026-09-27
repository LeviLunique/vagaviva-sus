package br.com.vagaviva.catalog.adapter.out.persistence;

import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase.HealthUnitFilter;
import br.com.vagaviva.catalog.application.port.out.HealthUnitRepository;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.shared.domain.ConflictException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class HealthUnitPersistenceAdapter implements HealthUnitRepository {

    private static final String CNES_CONSTRAINT = "health_unit_cnes_key";
    private static final Sort BY_NAME = Sort.by("name", "id");

    private final HealthUnitJpaRepository repository;

    HealthUnitPersistenceAdapter(HealthUnitJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public HealthUnit save(HealthUnit unit) {
        try {
            return repository.saveAndFlush(HealthUnitEntity.from(unit)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(CNES_CONSTRAINT)) {
                throw new ConflictException("CNES_ALREADY_REGISTERED", "Já existe uma unidade com este CNES.");
            }
            throw ex;
        }
    }

    @Override
    public Optional<HealthUnit> findById(UUID id) {
        return repository.findById(id).map(HealthUnitEntity::toDomain);
    }

    @Override
    public boolean existsByCnes(Cnes cnes) {
        return repository.existsByCnes(cnes.value());
    }

    @Override
    public Page<HealthUnit> search(HealthUnitFilter filter, Pageable pageable) {
        Pageable byName = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_NAME);
        return repository.findAll(matching(filter), byName).map(HealthUnitEntity::toDomain);
    }

    private static Specification<HealthUnitEntity> matching(HealthUnitFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.type() != null) {
                predicates.add(builder.equal(root.get("type"), filter.type()));
            }
            if (filter.municipalityCode() != null) {
                predicates.add(builder.equal(root.get("municipalityCode"), filter.municipalityCode()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
