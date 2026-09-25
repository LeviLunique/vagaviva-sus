package br.com.vagaviva.identity.application.service;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.CLOCK;
import static br.com.vagaviva.identity.fixtures.StaffUserFixture.NOW;
import static br.com.vagaviva.identity.fixtures.StaffUserFixture.aStaffUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase.LoginCommand;
import br.com.vagaviva.identity.application.port.out.IssuedToken;
import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.application.port.out.TokenIssuer;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.UnauthenticatedException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "regina@vagaviva.test";
    private static final LoginCommand GOOD = new LoginCommand(EMAIL, "Senha12345", "10.0.0.1");
    private static final LoginCommand BAD = new LoginCommand(EMAIL, "errada1234", "10.0.0.1");
    private static final IssuedToken TOKEN = new IssuedToken("jwt", Duration.ofMinutes(60));

    @Mock StaffUserRepository repository;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenIssuer tokenIssuer;
    @Mock IdentityAudit audit;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        var properties = new IdentityProperties(new IdentityProperties.Login(5, Duration.ofMinutes(15)),
                new IdentityProperties.BootstrapAdmin("admin@vagaviva.test", null));
        service = new AuthenticationService(repository, passwordHasher, tokenIssuer, audit, properties, CLOCK);
        lenient().when(passwordHasher.matches(eq("Senha12345"), anyString())).thenReturn(true);
        lenient().when(passwordHasher.matches(eq("errada1234"), anyString())).thenReturn(false);
        lenient().when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("credenciais válidas ⇒ emite o token e audita LOGIN_SUCCEEDED")
    void shouldIssueTokenForValidCredentials() {
        StaffUser user = aStaffUser().build();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenIssuer.issue(user)).thenReturn(TOKEN);

        var result = service.authenticate(GOOD);

        assertThat(result.token()).isEqualTo(TOKEN);
        assertThat(result.user()).isSameAs(user);
        verify(audit).login(user, IdentityAudit.LOGIN_SUCCEEDED, AuditOutcome.SUCCESS, "10.0.0.1", Map.of());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("login bem-sucedido após falhas zera o contador e grava o usuário")
    void shouldResetFailuresOnSuccess() {
        StaffUser user = aStaffUser().withFailedLoginAttempts(3).build();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenIssuer.issue(user)).thenReturn(TOKEN);

        service.authenticate(GOOD);

        verify(repository).save(user);
        assertThat(user.failedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("e-mail desconhecido ⇒ 401 genérico, verificando a senha contra hash fictício (tempo constante)")
    void shouldRejectUnknownEmail() {
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(GOOD))
                .isInstanceOf(UnauthenticatedException.class)
                .extracting("code").isEqualTo("INVALID_CREDENTIALS");
        verify(passwordHasher).matches(eq("Senha12345"), anyString());
        verify(audit).login(isNull(), eq(IdentityAudit.LOGIN_FAILED), eq(AuditOutcome.DENIED), eq("10.0.0.1"),
                eq(Map.of("reason", "UNKNOWN_USER")));
    }

    @Test
    @DisplayName("usuário inativo não autentica, mesmo com a senha certa")
    void shouldRejectInactiveUser() {
        StaffUser user = aStaffUser().inactive().build();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate(GOOD)).isInstanceOf(UnauthenticatedException.class);
        verify(audit).login(user, IdentityAudit.LOGIN_FAILED, AuditOutcome.DENIED, "10.0.0.1", Map.of("reason", "INACTIVE"));
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("usuário bloqueado não autentica, mesmo com a senha certa, e audita LOGIN_LOCKED")
    void shouldRejectLockedUser() {
        StaffUser user = aStaffUser().withFailedLoginAttempts(5).lockedUntil(NOW.plusSeconds(60)).build();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate(GOOD)).isInstanceOf(UnauthenticatedException.class);
        verify(audit).login(user, IdentityAudit.LOGIN_LOCKED, AuditOutcome.DENIED, "10.0.0.1", Map.of("reason", "LOCKED"));
    }

    @Test
    @DisplayName("senha errada conta a falha e audita LOGIN_FAILED")
    void shouldCountFailedAttempt() {
        StaffUser user = aStaffUser().build();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate(BAD)).isInstanceOf(UnauthenticatedException.class);

        assertThat(user.failedLoginAttempts()).isEqualTo(1);
        verify(repository).save(user);
        verify(audit).login(user, IdentityAudit.LOGIN_FAILED, AuditOutcome.DENIED, "10.0.0.1",
                Map.of("reason", "BAD_PASSWORD"));
    }

    @Test
    @DisplayName("RN-01: a 5ª senha errada bloqueia e audita LOGIN_LOCKED")
    void shouldLockOnFifthFailure() {
        StaffUser user = aStaffUser().withFailedLoginAttempts(4).build();
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate(BAD)).isInstanceOf(UnauthenticatedException.class);

        assertThat(user.isLocked(CLOCK)).isTrue();
        verify(audit).login(user, IdentityAudit.LOGIN_LOCKED, AuditOutcome.DENIED, "10.0.0.1",
                Map.of("reason", "BAD_PASSWORD"));
    }
}
