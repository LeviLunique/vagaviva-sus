package br.com.vagaviva.identity.domain;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.CLOCK;
import static br.com.vagaviva.identity.fixtures.StaffUserFixture.NOW;
import static br.com.vagaviva.identity.fixtures.StaffUserFixture.aStaffUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffUserTest {

    private static final LockoutPolicy POLICY = new LockoutPolicy(5, Duration.ofMinutes(15));

    @Test
    @DisplayName("cria profissional ativo, com e-mail normalizado e id UUIDv7")
    void shouldCreateActiveUserWithNormalizedEmail() {
        StaffUser user = StaffUser.create("  Maria Gestora ", " Maria@VagaViva.TEST ", "hash", Role.MANAGER, null, CLOCK);

        assertThat(user.isActive()).isTrue();
        assertThat(user.name()).isEqualTo("Maria Gestora");
        assertThat(user.email()).isEqualTo("maria@vagaviva.test");
        assertThat(user.id().version()).isEqualTo(7);
        assertThat(user.createdAt()).isEqualTo(NOW);
        assertThat(user.version()).isNull();
    }

    @Test
    @DisplayName("REQUESTER e SCHEDULER sem unidade de saúde ⇒ HEALTH_UNIT_REQUIRED")
    void shouldRequireHealthUnitForUnitScopedRoles() {
        assertThatThrownBy(() -> StaffUser.create("Ana", "ana@vagaviva.test", "hash", Role.REQUESTER, null, CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("HEALTH_UNIT_REQUIRED");

        StaffUser scheduler = StaffUser.create("Ana", "ana@vagaviva.test", "hash", Role.SCHEDULER, UUID.randomUUID(), CLOCK);
        assertThat(scheduler.healthUnitId()).isNotNull();
    }

    @Test
    @DisplayName("RN-01: a 5ª falha seguida bloqueia o login por 15 minutos")
    void shouldLockAfterFiveConsecutiveFailures() {
        StaffUser user = aStaffUser().build();

        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(POLICY, CLOCK);
        }
        assertThat(user.isLocked(CLOCK)).isFalse();

        user.recordFailedLogin(POLICY, CLOCK);

        assertThat(user.isLocked(CLOCK)).isTrue();
        assertThat(user.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(user.failedLoginAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("RN-01: passados os 15 minutos o bloqueio expira e a próxima falha recomeça a contagem")
    void shouldUnlockAfterLockDuration() {
        StaffUser user = aStaffUser().withFailedLoginAttempts(5).lockedUntil(NOW.plus(Duration.ofMinutes(15))).build();
        Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC);

        assertThat(user.isLocked(later)).isFalse();

        user.recordFailedLogin(POLICY, later);

        assertThat(user.failedLoginAttempts()).isEqualTo(1);
        assertThat(user.lockedUntil()).isNull();
        assertThat(user.updatedAt()).isEqualTo(later.instant());
    }

    @Test
    @DisplayName("RN-01: login bem-sucedido zera falhas e bloqueio; sem falhas não há alteração")
    void shouldResetCounterOnSuccessfulLogin() {
        StaffUser withFailures = aStaffUser().withFailedLoginAttempts(3).build();
        StaffUser clean = aStaffUser().build();

        assertThat(withFailures.recordSuccessfulLogin(CLOCK)).isTrue();
        assertThat(withFailures.failedLoginAttempts()).isZero();
        assertThat(clean.recordSuccessfulLogin(CLOCK)).isFalse();

        StaffUser lockedOnly = aStaffUser().lockedUntil(NOW.minusSeconds(1)).build();
        assertThat(lockedOnly.recordSuccessfulLogin(CLOCK)).isTrue();
        assertThat(lockedOnly.lockedUntil()).isNull();
    }

    @Test
    @DisplayName("desativar e reativar o profissional atualiza o status e o updatedAt")
    void shouldDeactivateAndActivate() {
        StaffUser user = aStaffUser().build();

        user.deactivate(CLOCK);
        assertThat(user.isActive()).isFalse();

        user.activate(CLOCK);
        assertThat(user.isActive()).isTrue();
        assertThat(user.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("política de bloqueio exige limite e duração positivos")
    void shouldValidateLockoutPolicy() {
        assertThatThrownBy(() -> new LockoutPolicy(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutPolicy(5, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutPolicy(5, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutPolicy(5, Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
