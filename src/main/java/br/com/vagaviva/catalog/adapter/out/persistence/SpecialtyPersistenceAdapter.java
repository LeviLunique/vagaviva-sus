package br.com.vagaviva.catalog.adapter.out.persistence;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.ConflictException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class SpecialtyPersistenceAdapter implements SpecialtyRepository {

    private static final String CODE_CONSTRAINT = "specialty_code_key";
    private static final Sort BY_NAME = Sort.by("name");

    private final SpecialtyJpaRepository repository;

    SpecialtyPersistenceAdapter(SpecialtyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Specialty save(Specialty specialty) {
        try {
            return repository.saveAndFlush(SpecialtyEntity.from(specialty)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(CODE_CONSTRAINT)) {
                throw new ConflictException("SPECIALTY_CODE_ALREADY_REGISTERED",
                        "Já existe uma especialidade com este código.");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Specialty> findById(UUID id) {
        return repository.findById(id).map(SpecialtyEntity::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public List<Specialty> findAll(@Nullable SpecialtyType type) {
        List<SpecialtyEntity> entities = type == null ? repository.findAll(BY_NAME) : repository.findByType(type, BY_NAME);
        return entities.stream().map(SpecialtyEntity::toDomain).toList();
    }
}
