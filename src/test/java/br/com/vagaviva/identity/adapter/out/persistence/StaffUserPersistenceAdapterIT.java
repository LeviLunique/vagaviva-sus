package br.com.vagaviva.identity.adapter.out.persistence;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class StaffUserPersistenceAdapterIT {

    @Autowired StaffUserRepository repository;

    @Test
    @DisplayName("grava e relê o profissional com todos os campos e versão")
    void shouldRoundTrip() {
        StaffUser saved = repository.save(newUser("Roundtrip@VagaViva.test", Role.SCHEDULER, UUID.randomUUID()));

        StaffUser loaded = repository.findById(saved.id()).orElseThrow();

        assertThat(loaded.email()).isEqualTo(saved.email());
        assertThat(loaded.role()).isEqualTo(Role.SCHEDULER);
        assertThat(loaded.healthUnitId()).isEqualTo(saved.healthUnitId());
        assertThat(loaded.version()).isZero();
    }

    @Test
    @DisplayName("RF-02 CA1: e-mail é único sem diferenciar maiúsculas (índice lower(email))")
    void shouldEnforceCaseInsensitiveUniqueEmail() {
        String email = unique("duplicado");
        repository.save(newUser(email, Role.MANAGER, null));

        assertThat(repository.existsByEmail(email.toUpperCase())).isTrue();
        assertThat(repository.findByEmail(" " + email.toUpperCase() + " ")).isPresent();
        StaffUser twin = StaffUser.restore(UUID.randomUUID(), "Gêmeo", email.toUpperCase(), "$2a$12$h", Role.MANAGER,
                null, true, 0, null, CLOCK.instant(), CLOCK.instant(), null);
        assertThatThrownBy(() -> repository.save(twin))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("alteração com versão desatualizada é rejeitada (optimistic locking)")
    void shouldRejectStaleUpdate() {
        StaffUser saved = repository.save(newUser(unique("versao"), Role.REGULATOR, null));
        StaffUser first = repository.findById(saved.id()).orElseThrow();
        StaffUser stale = repository.findById(saved.id()).orElseThrow();

        first.deactivate(CLOCK);
        repository.save(first);
        stale.recordFailedLogin(new br.com.vagaviva.identity.domain.LockoutPolicy(5, Duration.ofMinutes(15)), CLOCK);

        assertThatThrownBy(() -> repository.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("filtra por papel e status, ordenando por nome")
    void shouldSearchByRoleAndStatus() {
        StaffUser inactive = newUser(unique("inativo"), Role.MANAGER, null);
        inactive.deactivate(CLOCK);
        repository.save(inactive);

        var inactiveManagers = repository.search(new StaffUserFilter(Role.MANAGER, false), PageRequest.of(0, 100));
        var all = repository.search(new StaffUserFilter(null, null), PageRequest.of(0, 100));

        assertThat(inactiveManagers.getContent()).allMatch(user -> user.role() == Role.MANAGER && !user.isActive());
        assertThat(inactiveManagers.getContent()).extracting(StaffUser::id).contains(inactive.id());
        assertThat(all.getContent()).extracting(StaffUser::name).isSortedAccordingTo(String::compareTo);
        assertThat(repository.existsByRole(Role.MANAGER)).isTrue();
    }

    private static StaffUser newUser(String email, Role role, UUID unitId) {
        return StaffUser.create("Profissional " + email, email, "$2a$12$hash", role, unitId, CLOCK);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@vagaviva.test";
    }
}
