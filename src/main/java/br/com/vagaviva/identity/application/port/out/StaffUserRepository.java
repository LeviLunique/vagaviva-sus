package br.com.vagaviva.identity.application.port.out;

import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StaffUserRepository {

    /**
     * Grava e devolve o estado persistido (com a nova versão).
     *
     * @throws br.com.vagaviva.shared.domain.ConflictException se o e-mail já existir
     */
    StaffUser save(StaffUser user);

    Optional<StaffUser> findById(UUID id);

    /** Busca sem diferenciar maiúsculas. */
    Optional<StaffUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    Page<StaffUser> search(StaffUserFilter filter, Pageable pageable);
}
