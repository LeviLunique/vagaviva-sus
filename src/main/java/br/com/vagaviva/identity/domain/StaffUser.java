package br.com.vagaviva.identity.domain;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.Ids;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Profissional com acesso ao sistema (agregado do módulo identity). */
public final class StaffUser {

    private final UUID id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final @Nullable UUID healthUnitId;
    private boolean active;
    private int failedLoginAttempts;
    private @Nullable Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;
    private final @Nullable Long version;

    private StaffUser(UUID id, String name, String email, String passwordHash, Role role,
            @Nullable UUID healthUnitId, boolean active, int failedLoginAttempts, @Nullable Instant lockedUntil,
            Instant createdAt, Instant updatedAt, @Nullable Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.healthUnitId = healthUnitId;
        this.active = active;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /** Novo profissional ativo. {@code REQUESTER}/{@code SCHEDULER} exigem unidade (RF-02 CA3). */
    public static StaffUser create(String name, String email, String passwordHash, Role role,
            @Nullable UUID healthUnitId, Clock clock) {
        if (role.requiresHealthUnit() && healthUnitId == null) {
            throw new BusinessRuleException("HEALTH_UNIT_REQUIRED",
                    "O papel %s exige a unidade de saúde (healthUnitId).".formatted(role));
        }
        Instant now = clock.instant();
        return new StaffUser(Ids.newId(), name.strip(), normalizeEmail(email), passwordHash, role, healthUnitId,
                true, 0, null, now, now, null);
    }

    /** Reconstrói o agregado a partir da persistência. */
    public static StaffUser restore(UUID id, String name, String email, String passwordHash, Role role,
            @Nullable UUID healthUnitId, boolean active, int failedLoginAttempts, @Nullable Instant lockedUntil,
            Instant createdAt, Instant updatedAt, @Nullable Long version) {
        return new StaffUser(id, name, email, passwordHash, role, healthUnitId, active, failedLoginAttempts,
                lockedUntil, createdAt, updatedAt, version);
    }

    /** E-mail é comparado sem diferenciar maiúsculas (RF-02 CA1). */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(Clock clock) {
        return lockedUntil != null && clock.instant().isBefore(lockedUntil);
    }

    /**
     * RN-01: conta a falha e, ao atingir o limite, bloqueia. Um bloqueio já vencido é descartado
     * antes — o usuário volta a ter todas as tentativas.
     */
    public void recordFailedLogin(LockoutPolicy policy, Clock clock) {
        Instant now = clock.instant();
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
        }
        updatedAt = now;
    }

    /** RN-01: login bem-sucedido zera o contador. Devolve {@code false} se não havia nada a zerar. */
    public boolean recordSuccessfulLogin(Clock clock) {
        if (failedLoginAttempts == 0 && lockedUntil == null) {
            return false;
        }
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = clock.instant();
        return true;
    }

    public void deactivate(Clock clock) {
        active = false;
        updatedAt = clock.instant();
    }

    public void activate(Clock clock) {
        active = true;
        updatedAt = clock.instant();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public @Nullable UUID healthUnitId() {
        return healthUnitId;
    }

    public boolean isActive() {
        return active;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public @Nullable Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Long version() {
        return version;
    }
}
