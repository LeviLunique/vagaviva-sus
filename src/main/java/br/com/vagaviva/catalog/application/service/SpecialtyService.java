package br.com.vagaviva.catalog.application.service;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.in.SpecialtyUseCases;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Specialty;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SpecialtyService implements SpecialtyUseCases {

    private final SpecialtyRepository repository;
    private final Clock clock;

    SpecialtyService(SpecialtyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Specialty register(RegisterSpecialtyCommand command) {
        Specialty specialty = Specialty.register(command.code(), command.name(), command.type(), command.sensitive(),
                clock);
        if (repository.existsByCode(specialty.code())) {
            throw CatalogErrors.specialtyCodeAlreadyRegistered();
        }
        return repository.save(specialty);
    }

    @Override
    @Transactional(readOnly = true)
    public Specialty get(UUID id) {
        return repository.findById(id).orElseThrow(CatalogErrors::specialtyNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Specialty> list(@Nullable SpecialtyType type) {
        return repository.findAll(type);
    }
}
