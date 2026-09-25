package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.application.port.in.CreateStaffUserUseCase;
import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.PasswordPolicy;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.ConflictException;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CreateStaffUserService implements CreateStaffUserUseCase {

    private final StaffUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final IdentityAudit audit;
    private final Clock clock;

    CreateStaffUserService(StaffUserRepository repository, PasswordHasher passwordHasher, IdentityAudit audit,
            Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StaffUser create(CreateStaffUserCommand command) {
        PasswordPolicy.validate(command.password());
        if (repository.existsByEmail(command.email())) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "Já existe um profissional com este e-mail.");
        }
        StaffUser user = repository.save(StaffUser.create(command.name(), command.email(),
                passwordHasher.hash(command.password()), command.role(), command.healthUnitId(), clock));
        audit.administration(command.actor(), user, IdentityAudit.STAFF_USER_CREATED, command.clientIp(),
                Map.of("role", user.role().name()));
        return user;
    }
}
