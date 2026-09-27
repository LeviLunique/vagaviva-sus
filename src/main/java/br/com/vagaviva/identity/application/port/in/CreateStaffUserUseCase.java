package br.com.vagaviva.identity.application.port.in;

import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RF-02: ADMIN cadastra profissional com papel e, quando exigido, unidade. */
public interface CreateStaffUserUseCase {

    StaffUser create(CreateStaffUserCommand command);

    record CreateStaffUserCommand(String name, String email, String password, Role role,
            @Nullable UUID healthUnitId, CurrentUser actor, @Nullable String clientIp) {
    }
}
