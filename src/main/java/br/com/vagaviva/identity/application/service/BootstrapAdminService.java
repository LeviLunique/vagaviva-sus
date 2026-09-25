package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.application.port.in.BootstrapAdminUseCase;
import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.PasswordPolicy;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-03: sem nenhum ADMIN, cria o administrador inicial com a senha do segredo. */
@Service
class BootstrapAdminService implements BootstrapAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final StaffUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final IdentityAudit audit;
    private final IdentityProperties.BootstrapAdmin properties;
    private final Clock clock;

    BootstrapAdminService(StaffUserRepository repository, PasswordHasher passwordHasher, IdentityAudit audit,
            IdentityProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.properties = properties.bootstrapAdmin();
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean ensureAdminExists() {
        if (repository.existsByRole(Role.ADMIN)) {
            return false;
        }
        String password = properties.password();
        if (password == null || password.isBlank()) {
            log.warn("Nenhum ADMIN cadastrado e BOOTSTRAP_ADMIN_PASSWORD ausente: administrador inicial não criado.");
            return false;
        }
        PasswordPolicy.validate(password);
        // Com várias instâncias subindo juntas, o índice único de e-mail garante um só admin: a
        // instância que perder a corrida falha no startup e, ao reiniciar, encontra o admin criado.
        StaffUser admin = repository.save(StaffUser.create("Administrador", properties.email(),
                passwordHasher.hash(password), Role.ADMIN, null, clock));
        audit.administration(null, admin, IdentityAudit.STAFF_USER_CREATED, null,
                Map.of("role", Role.ADMIN.name(), "bootstrap", "true"));
        log.info("Administrador inicial criado (id={}).", admin.id());
        return true;
    }
}
