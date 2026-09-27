package br.com.vagaviva.patient.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatientJpaRepository extends JpaRepository<PatientEntity, UUID> {

    Optional<PatientEntity> findByCns(String cns);

    Optional<PatientEntity> findByCpf(String cpf);

    boolean existsByCns(String cns);

    boolean existsByCpf(String cpf);
}
