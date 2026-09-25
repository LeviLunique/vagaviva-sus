package br.com.vagaviva.identity.fixtures;

import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Test Data Builder de {@link StaffUser} — apenas dados fictícios. */
public final class StaffUserFixture {

    public static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private UUID id = UUID.fromString("0199a0b0-0000-7000-8000-000000000001");
    private String name = "Regina Reguladora";
    private String email = "regina@vagaviva.test";
    private String passwordHash = "$2a$12$hash";
    private Role role = Role.REGULATOR;
    private UUID healthUnitId;
    private boolean active = true;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Long version = 0L;

    private StaffUserFixture() {
    }

    public static StaffUserFixture aStaffUser() {
        return new StaffUserFixture();
    }

    public StaffUserFixture withId(UUID id) {
        this.id = id;
        return this;
    }

    public StaffUserFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public StaffUserFixture withRole(Role role) {
        this.role = role;
        if (role.requiresHealthUnit() && healthUnitId == null) {
            this.healthUnitId = UUID.fromString("0199a0b0-0000-7000-8000-0000000000aa");
        }
        return this;
    }

    public StaffUserFixture inactive() {
        this.active = false;
        return this;
    }

    public StaffUserFixture withFailedLoginAttempts(int attempts) {
        this.failedLoginAttempts = attempts;
        return this;
    }

    public StaffUserFixture lockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
        return this;
    }

    public StaffUser build() {
        return StaffUser.restore(id, name, email, passwordHash, role, healthUnitId, active, failedLoginAttempts,
                lockedUntil, NOW.minusSeconds(86_400), NOW.minusSeconds(86_400), version);
    }
}
