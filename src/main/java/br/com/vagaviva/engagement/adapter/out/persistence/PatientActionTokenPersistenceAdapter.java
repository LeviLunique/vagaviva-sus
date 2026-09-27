package br.com.vagaviva.engagement.adapter.out.persistence;

import br.com.vagaviva.engagement.application.port.out.PatientActionTokenRepository;
import br.com.vagaviva.engagement.domain.PatientActionToken;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class PatientActionTokenPersistenceAdapter implements PatientActionTokenRepository {

    private final PatientActionTokenJpaRepository repository;

    PatientActionTokenPersistenceAdapter(PatientActionTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PatientActionToken save(PatientActionToken token) {
        return repository.saveAndFlush(PatientActionTokenEntity.from(token)).toDomain();
    }

    @Override
    public Optional<PatientActionToken> findByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(PatientActionTokenEntity::toDomain);
    }
}
