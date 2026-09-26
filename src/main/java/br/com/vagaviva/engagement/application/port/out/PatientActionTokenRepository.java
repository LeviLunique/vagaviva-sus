package br.com.vagaviva.engagement.application.port.out;

import br.com.vagaviva.engagement.domain.PatientActionToken;
import java.util.Optional;

public interface PatientActionTokenRepository {

    PatientActionToken save(PatientActionToken token);

    Optional<PatientActionToken> findByHash(String tokenHash);
}
