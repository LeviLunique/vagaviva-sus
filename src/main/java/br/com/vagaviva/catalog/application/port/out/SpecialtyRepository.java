package br.com.vagaviva.catalog.application.port.out;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.domain.Specialty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface SpecialtyRepository {

    /** @throws br.com.vagaviva.shared.domain.ConflictException se o código já existir */
    Specialty save(Specialty specialty);

    Optional<Specialty> findById(UUID id);

    boolean existsByCode(String code);

    List<Specialty> findAll(@Nullable SpecialtyType type);
}
