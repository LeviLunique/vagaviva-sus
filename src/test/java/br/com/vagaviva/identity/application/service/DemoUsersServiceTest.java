package br.com.vagaviva.identity.application.service;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoUsersServiceTest {

    @Mock StaffUserRepository repository;
    @Mock PasswordHasher passwordHasher;
    @Mock HealthUnitAssignmentPolicy unitPolicy;
    @Mock IdentityAudit audit;

    private DemoUsersService service(String password) {
        return new DemoUsersService(repository, passwordHasher, unitPolicy, audit, new DemoProperties(true, password), CLOCK);
    }

    @Test
    @DisplayName("RF-11: cria um usuário por papel, REQUESTER e SCHEDULER nas unidades do seed")
    void shouldCreateOneUserPerRole() {
        when(passwordHasher.hash("Demo@Test2026")).thenReturn("$2a$12$hash");
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service("Demo@Test2026").seed()).isEqualTo(4);

        verify(repository).save(argThat(user -> user.role() == Role.REQUESTER
                && DemoUsersService.DEMO_PRIMARY_CARE_UNIT.equals(user.healthUnitId())));
        verify(repository).save(argThat(user -> user.role() == Role.SCHEDULER
                && DemoUsersService.DEMO_SPECIALIZED_UNIT.equals(user.healthUnitId())));
        verify(unitPolicy).check(Role.SCHEDULER, DemoUsersService.DEMO_SPECIALIZED_UNIT);
        verify(audit, times(4)).administration(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("idempotente: usuários que já existem não são recriados")
    void shouldSkipExistingUsers() {
        when(repository.existsByEmail(anyString())).thenReturn(true);

        assertThat(service("Demo@Test2026").seed()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("sem DEMO_USERS_PASSWORD apenas avisa; senha fora da política falha no startup")
    void shouldRequireValidPassword() {
        assertThat(service(null).seed()).isZero();
        assertThat(service(" ").seed()).isZero();
        assertThatThrownBy(() -> service("fraca").seed()).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }
}
