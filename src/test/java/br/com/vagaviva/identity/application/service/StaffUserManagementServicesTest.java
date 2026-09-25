package br.com.vagaviva.identity.application.service;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.CLOCK;
import static br.com.vagaviva.identity.fixtures.StaffUserFixture.aStaffUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.identity.application.port.in.ChangeStaffUserStatusUseCase.ChangeStaffUserStatusCommand;
import br.com.vagaviva.identity.application.port.in.CreateStaffUserUseCase.CreateStaffUserCommand;
import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class StaffUserManagementServicesTest {

    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), Role.ADMIN, null, "Admin");

    @Mock StaffUserRepository repository;
    @Mock PasswordHasher passwordHasher;
    @Mock IdentityAudit audit;

    @Nested
    class Create {

        private CreateStaffUserService service() {
            return new CreateStaffUserService(repository, passwordHasher, audit, CLOCK);
        }

        @Test
        @DisplayName("cadastra com a senha em hash BCrypt e audita STAFF_USER_CREATED")
        void shouldCreateUserWithHashedPassword() {
            when(passwordHasher.hash("Senha12345")).thenReturn("$2a$12$hash");
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

            StaffUser user = service().create(command("Senha12345"));

            assertThat(user.passwordHash()).isEqualTo("$2a$12$hash");
            assertThat(user.email()).isEqualTo("novo@vagaviva.test");
            verify(audit).administration(ADMIN, user, IdentityAudit.STAFF_USER_CREATED, "10.0.0.9",
                    Map.of("role", "REGULATOR"));
        }

        @Test
        @DisplayName("senha fora da política ⇒ WEAK_PASSWORD antes de qualquer acesso ao banco")
        void shouldRejectWeakPassword() {
            assertThatThrownBy(() -> service().create(command("curta")))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo("WEAK_PASSWORD");
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("e-mail já cadastrado ⇒ EMAIL_ALREADY_REGISTERED (409)")
        void shouldRejectDuplicatedEmail() {
            when(repository.existsByEmail(" Novo@VagaViva.test ")).thenReturn(true);

            assertThatThrownBy(() -> service().create(command("Senha12345")))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("EMAIL_ALREADY_REGISTERED");
            verify(repository, never()).save(any());
        }

        private CreateStaffUserCommand command(String password) {
            return new CreateStaffUserCommand("Novo Regulador", " Novo@VagaViva.test ", password, Role.REGULATOR,
                    null, ADMIN, "10.0.0.9");
        }
    }

    @Nested
    class ChangeStatus {

        private ChangeStaffUserStatusService service() {
            return new ChangeStaffUserStatusService(repository, audit, CLOCK);
        }

        @Test
        @DisplayName("desativa o profissional e audita STAFF_USER_DEACTIVATED")
        void shouldDeactivate() {
            StaffUser target = aStaffUser().build();
            when(repository.findById(target.id())).thenReturn(Optional.of(target));
            when(repository.save(target)).thenReturn(target);

            StaffUser result = service().change(new ChangeStaffUserStatusCommand(target.id(), false, ADMIN, null));

            assertThat(result.isActive()).isFalse();
            verify(audit).administration(ADMIN, target, IdentityAudit.STAFF_USER_DEACTIVATED, null, Map.of());
        }

        @Test
        @DisplayName("reativa o profissional e audita STAFF_USER_ACTIVATED")
        void shouldActivate() {
            StaffUser target = aStaffUser().inactive().build();
            when(repository.findById(target.id())).thenReturn(Optional.of(target));
            when(repository.save(target)).thenReturn(target);

            StaffUser result = service().change(new ChangeStaffUserStatusCommand(target.id(), true, ADMIN, null));

            assertThat(result.isActive()).isTrue();
            verify(audit).administration(ADMIN, target, IdentityAudit.STAFF_USER_ACTIVATED, null, Map.of());
        }

        @Test
        @DisplayName("o ADMIN não pode desativar a si mesmo ⇒ CANNOT_DEACTIVATE_SELF (409)")
        void shouldNotDeactivateSelf() {
            assertThatThrownBy(() -> service().change(new ChangeStaffUserStatusCommand(ADMIN.id(), false, ADMIN, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("CANNOT_DEACTIVATE_SELF");
        }

        @Test
        @DisplayName("reativar a si mesmo é permitido (não é desativação)")
        void shouldAllowReactivatingSelf() {
            StaffUser self = aStaffUser().withId(ADMIN.id()).withRole(Role.ADMIN).build();
            when(repository.findById(ADMIN.id())).thenReturn(Optional.of(self));
            when(repository.save(self)).thenReturn(self);

            assertThat(service().change(new ChangeStaffUserStatusCommand(ADMIN.id(), true, ADMIN, null)).isActive()).isTrue();
        }

        @Test
        @DisplayName("profissional inexistente ⇒ STAFF_USER_NOT_FOUND (404)")
        void shouldFailWhenUserDoesNotExist() {
            UUID unknown = UUID.randomUUID();
            when(repository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().change(new ChangeStaffUserStatusCommand(unknown, false, ADMIN, null)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo("STAFF_USER_NOT_FOUND");
        }
    }

    @Nested
    class Queries {

        @Test
        @DisplayName("lista delegando filtros e paginação ao repositório")
        void shouldListWithFilters() {
            var filter = new StaffUserFilter(Role.ADMIN, true);
            var page = new PageImpl<>(List.of(aStaffUser().build()));
            when(repository.search(filter, PageRequest.of(0, 20))).thenReturn(page);

            assertThat(new ListStaffUsersService(repository).list(filter, PageRequest.of(0, 20))).isSameAs(page);
        }

        @Test
        @DisplayName("consulta por id; inexistente ⇒ 404")
        void shouldGetById() {
            StaffUser user = aStaffUser().build();
            when(repository.findById(user.id())).thenReturn(Optional.of(user));
            var service = new GetStaffUserService(repository);

            assertThat(service.get(user.id())).isSameAs(user);
            assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Bootstrap {

        private BootstrapAdminService service(String password) {
            var properties = new IdentityProperties(new IdentityProperties.Login(5, Duration.ofMinutes(15)),
                    new IdentityProperties.BootstrapAdmin("admin@vagaviva.test", password));
            return new BootstrapAdminService(repository, passwordHasher, audit, properties, CLOCK);
        }

        @Test
        @DisplayName("RF-03: sem ADMIN, cria o administrador inicial com a senha do segredo")
        void shouldCreateInitialAdmin() {
            when(repository.existsByRole(Role.ADMIN)).thenReturn(false);
            when(passwordHasher.hash("Admin@Test2026")).thenReturn("$2a$12$hash");
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

            assertThat(service("Admin@Test2026").ensureAdminExists()).isTrue();

            verify(audit).administration(isNull(), any(), eq(IdentityAudit.STAFF_USER_CREATED), isNull(),
                    eq(Map.of("role", "ADMIN", "bootstrap", "true")));
        }

        @Test
        @DisplayName("já existindo ADMIN, não faz nada (idempotente)")
        void shouldSkipWhenAdminExists() {
            when(repository.existsByRole(Role.ADMIN)).thenReturn(true);

            assertThat(service("Admin@Test2026").ensureAdminExists()).isFalse();
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("sem BOOTSTRAP_ADMIN_PASSWORD, apenas avisa e não cria")
        void shouldSkipWithoutPassword() {
            when(repository.existsByRole(Role.ADMIN)).thenReturn(false);

            assertThat(service(" ").ensureAdminExists()).isFalse();
            assertThat(service(null).ensureAdminExists()).isFalse();
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("senha do segredo fora da política ⇒ falha no startup (WEAK_PASSWORD)")
        void shouldRejectWeakBootstrapPassword() {
            when(repository.existsByRole(Role.ADMIN)).thenReturn(false);

            assertThatThrownBy(() -> service("fraca").ensureAdminExists()).isInstanceOf(BusinessRuleException.class);
        }
    }
}
