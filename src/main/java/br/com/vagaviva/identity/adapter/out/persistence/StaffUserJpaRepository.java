package br.com.vagaviva.identity.adapter.out.persistence;

import br.com.vagaviva.shared.security.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StaffUserJpaRepository extends JpaRepository<StaffUserEntity, UUID>,
        JpaSpecificationExecutor<StaffUserEntity> {

    /** {@code lower(email)} usa o índice único {@code ux_staff_user_email}. */
    @Query("select u from StaffUserEntity u where lower(u.email) = lower(:email)")
    Optional<StaffUserEntity> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from StaffUserEntity u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByRole(Role role);
}
