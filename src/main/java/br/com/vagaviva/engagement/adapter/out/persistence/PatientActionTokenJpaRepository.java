package br.com.vagaviva.engagement.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatientActionTokenJpaRepository extends JpaRepository<PatientActionTokenEntity, UUID> {

    Optional<PatientActionTokenEntity> findByTokenHash(String tokenHash);
}
