package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase;
import br.com.vagaviva.identity.application.port.out.IssuedToken;
import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.application.port.out.TokenIssuer;
import br.com.vagaviva.identity.domain.LockoutPolicy;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.UnauthenticatedException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-01 / RN-01. A resposta de falha é sempre a mesma (401 genérico) — não revela se o e-mail
 * existe, se o usuário está inativo ou bloqueado. A senha é verificada em todos os caminhos
 * (inclusive e-mail inexistente, contra um hash fictício) para que o tempo de resposta também
 * não revele nada.
 */
@Service
class AuthenticationService implements AuthenticateUseCase {

    private final StaffUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final IdentityAudit audit;
    private final LockoutPolicy lockoutPolicy;
    private final Clock clock;
    /** Hash de uma senha aleatória, gerado no startup: e-mail inexistente custa o mesmo BCrypt. */
    private final String unknownUserHash;

    AuthenticationService(StaffUserRepository repository, PasswordHasher passwordHasher, TokenIssuer tokenIssuer,
            IdentityAudit audit, IdentityProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.audit = audit;
        this.lockoutPolicy = properties.login().toPolicy();
        this.clock = clock;
        this.unknownUserHash = passwordHasher.hash(UUID.randomUUID() + "-x1");
    }

    /** Falhas gravam contador e auditoria: por isso a exceção de credencial não desfaz a transação. */
    @Override
    @Transactional(noRollbackFor = UnauthenticatedException.class)
    public AuthenticationResult authenticate(LoginCommand command) {
        Optional<StaffUser> found = repository.findByEmail(command.email());
        String hash = found.map(StaffUser::passwordHash).orElse(unknownUserHash);
        boolean passwordMatches = passwordHasher.matches(command.password(), hash);

        if (found.isEmpty()) {
            throw denied(null, IdentityAudit.LOGIN_FAILED, "UNKNOWN_USER", command);
        }
        StaffUser user = found.get();
        if (!user.isActive()) {
            throw denied(user, IdentityAudit.LOGIN_FAILED, "INACTIVE", command);
        }
        if (user.isLocked(clock)) {
            throw denied(user, IdentityAudit.LOGIN_LOCKED, "LOCKED", command);
        }
        if (!passwordMatches) {
            user.recordFailedLogin(lockoutPolicy, clock);
            StaffUser saved = repository.save(user);
            String action = saved.isLocked(clock) ? IdentityAudit.LOGIN_LOCKED : IdentityAudit.LOGIN_FAILED;
            throw denied(saved, action, "BAD_PASSWORD", command);
        }
        if (user.recordSuccessfulLogin(clock)) {
            user = repository.save(user);
        }
        IssuedToken token = tokenIssuer.issue(user);
        audit.login(user, IdentityAudit.LOGIN_SUCCEEDED, AuditOutcome.SUCCESS, command.clientIp(), Map.of());
        return new AuthenticationResult(token, user);
    }

    private UnauthenticatedException denied(StaffUser user, String action, String reason, LoginCommand command) {
        audit.login(user, action, AuditOutcome.DENIED, command.clientIp(), Map.of("reason", reason));
        return new UnauthenticatedException("INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }
}
