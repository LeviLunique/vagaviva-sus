package br.com.vagaviva.catalog.application.port.in;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.domain.Specialty;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RF-07: cadastro e consulta de especialidades (lista curta, sem paginação). */
public interface SpecialtyUseCases {

    Specialty register(RegisterSpecialtyCommand command);

    /** @throws br.com.vagaviva.shared.domain.NotFoundException {@code SPECIALTY_NOT_FOUND} */
    Specialty get(UUID id);

    /** Ordenadas por nome. */
    List<Specialty> list(@Nullable SpecialtyType type);

    record RegisterSpecialtyCommand(String code, String name, SpecialtyType type, boolean sensitive) {
    }
}
