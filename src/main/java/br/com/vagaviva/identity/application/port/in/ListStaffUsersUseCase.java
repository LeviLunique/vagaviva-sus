package br.com.vagaviva.identity.application.port.in;

import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListStaffUsersUseCase {

    /** Ordenado por nome. */
    Page<StaffUser> list(StaffUserFilter filter, Pageable pageable);

    record StaffUserFilter(@Nullable Role role, @Nullable Boolean active) {
    }
}
