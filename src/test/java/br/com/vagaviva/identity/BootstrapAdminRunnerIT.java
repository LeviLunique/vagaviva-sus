package br.com.vagaviva.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.identity.application.port.in.BootstrapAdminUseCase;
import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class BootstrapAdminRunnerIT {

    @Autowired StaffUserRepository repository;
    @Autowired BootstrapAdminUseCase bootstrapAdmin;

    @Test
    @DisplayName("RF-03: no startup o administrador inicial é criado uma única vez, com senha em BCrypt")
    void shouldCreateInitialAdminOnStartup() {
        StaffUser admin = repository.findByEmail("admin@vagaviva.test").orElseThrow();

        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.passwordHash()).startsWith("$2a$12$");

        assertThat(bootstrapAdmin.ensureAdminExists()).isFalse();
        assertThat(repository.search(new StaffUserFilter(Role.ADMIN, null), PageRequest.of(0, 100)).getContent())
                .filteredOn(user -> user.email().equals("admin@vagaviva.test")).hasSize(1);
    }
}
