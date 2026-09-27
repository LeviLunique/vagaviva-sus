package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.application.port.in.ChangeStaffUserStatusUseCase;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.ConflictException;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChangeStaffUserStatusService implements ChangeStaffUserStatusUseCase {

    private final StaffUserRepository repository;
    private final IdentityAudit audit;
    private final Clock clock;

    ChangeStaffUserStatusService(StaffUserRepository repository, IdentityAudit audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StaffUser change(ChangeStaffUserStatusCommand command) {
        if (!command.active() && command.userId().equals(command.actor().id())) {
            throw new ConflictException("CANNOT_DEACTIVATE_SELF", "Você não pode desativar o próprio usuário.");
        }
        StaffUser user = repository.findById(command.userId())
                .orElseThrow(GetStaffUserService.StaffUserNotFound::new);
        if (command.active()) {
            user.activate(clock);
        } else {
            user.deactivate(clock);
        }
        StaffUser saved = repository.save(user);
        String action = command.active() ? IdentityAudit.STAFF_USER_ACTIVATED : IdentityAudit.STAFF_USER_DEACTIVATED;
        audit.administration(command.actor(), saved, action, command.clientIp(), Map.of());
        return saved;
    }
}
