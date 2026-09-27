package br.com.vagaviva.identity.adapter.in.web;

import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Instant;
import java.util.UUID;

/** Profissional como exposto pela API — nunca inclui o hash da senha (RF-02 CA2). */
public record StaffUserResponse(UUID id, String name, String email, Role role, UUID healthUnitId, boolean active,
        Instant createdAt) {

    static StaffUserResponse from(StaffUser user) {
        return new StaffUserResponse(user.id(), user.name(), user.email(), user.role(), user.healthUnitId(),
                user.isActive(), user.createdAt());
    }
}
