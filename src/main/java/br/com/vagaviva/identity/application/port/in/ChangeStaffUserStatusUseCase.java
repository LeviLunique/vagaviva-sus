package br.com.vagaviva.identity.application.port.in;

import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.CurrentUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RF-02: ativar/desativar profissional (o ADMIN não pode desativar a si mesmo). */
public interface ChangeStaffUserStatusUseCase {

    StaffUser change(ChangeStaffUserStatusCommand command);

    record ChangeStaffUserStatusCommand(UUID userId, boolean active, CurrentUser actor, @Nullable String clientIp) {
    }
}
