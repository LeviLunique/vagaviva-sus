package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.application.port.in.SeedDemoUsersUseCase;
import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.PasswordPolicy;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuários de demonstração, um por papel, vinculados às unidades fixas do seed
 * ({@code db/seed/R__seed_demo_catalog_patients.sql}). Idempotente: só cria o que falta.
 */
@Service
class DemoUsersService implements SeedDemoUsersUseCase {

    /** UBS Vila Esperança e AME Zona Norte no seed. */
    static final UUID DEMO_PRIMARY_CARE_UNIT = UUID.fromString("0199c0de-0000-7000-8000-000000000101");
    static final UUID DEMO_SPECIALIZED_UNIT = UUID.fromString("0199c0de-0000-7000-8000-000000000201");

    private static final Logger log = LoggerFactory.getLogger(DemoUsersService.class);

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("Rita Solicitante", "requester@vagaviva.local", Role.REQUESTER, DEMO_PRIMARY_CARE_UNIT),
            new DemoUser("Renato Regulador", "regulator@vagaviva.local", Role.REGULATOR, null),
            new DemoUser("Sônia Agendadora", "scheduler@vagaviva.local", Role.SCHEDULER, DEMO_SPECIALIZED_UNIT),
            new DemoUser("Marcos Gestor", "manager@vagaviva.local", Role.MANAGER, null));

    private final StaffUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final HealthUnitAssignmentPolicy unitPolicy;
    private final IdentityAudit audit;
    private final DemoProperties properties;
    private final Clock clock;

    DemoUsersService(StaffUserRepository repository, PasswordHasher passwordHasher,
            HealthUnitAssignmentPolicy unitPolicy, IdentityAudit audit, DemoProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.unitPolicy = unitPolicy;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int seed() {
        String password = properties.usersPassword();
        if (password == null || password.isBlank()) {
            log.warn("Demonstração habilitada, mas DEMO_USERS_PASSWORD ausente: usuários de demonstração não criados.");
            return 0;
        }
        PasswordPolicy.validate(password);
        int created = 0;
        for (DemoUser demo : USERS) {
            if (!repository.existsByEmail(demo.email())) {
                unitPolicy.check(demo.role(), demo.unitId());
                StaffUser user = repository.save(StaffUser.create(demo.name(), demo.email(),
                        passwordHasher.hash(password), demo.role(), demo.unitId(), clock));
                audit.administration(null, user, IdentityAudit.STAFF_USER_CREATED, null,
                        Map.of("role", demo.role().name(), "demo", "true"));
                created++;
            }
        }
        if (created > 0) {
            log.info("{} usuário(s) de demonstração criado(s).", created);
        }
        return created;
    }

    private record DemoUser(String name, String email, Role role, @Nullable UUID unitId) {
    }
}
