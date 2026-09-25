package br.com.vagaviva.regulation.adapter.out.persistence;

import br.com.vagaviva.regulation.ReferralStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ReferralJpaRepository extends JpaRepository<ReferralEntity, UUID>, JpaSpecificationExecutor<ReferralEntity> {

    Optional<ReferralEntity> findByProtocol(String protocol);

    boolean existsByPatientIdAndSpecialtyIdAndStatusIn(UUID patientId, UUID specialtyId,
            Collection<ReferralStatus> statuses);

    Page<ReferralEntity> findByStatusAndSpecialtyId(ReferralStatus status, UUID specialtyId, Pageable pageable);
}
