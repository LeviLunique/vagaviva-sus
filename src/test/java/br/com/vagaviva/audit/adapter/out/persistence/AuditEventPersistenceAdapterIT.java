package br.com.vagaviva.audit.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.application.port.in.AuditEventQuery;
import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import br.com.vagaviva.audit.domain.AuditEvent;
import br.com.vagaviva.shared.domain.Ids;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class AuditEventPersistenceAdapterIT {

    @Autowired AuditEventRepository repository;

    @Test
    @DisplayName("grava details em jsonb e filtra por recurso, ator e período, mais recentes primeiro")
    void shouldAppendAndSearch() {
        UUID actor = UUID.randomUUID();
        String resourceId = UUID.randomUUID().toString();
        Instant base = Instant.parse("2026-09-20T10:00:00Z");
        repository.append(event(actor, resourceId, base, Map.of("reason", "BAD_PASSWORD")));
        repository.append(event(actor, resourceId, base.plusSeconds(60), Map.of()));
        repository.append(event(UUID.randomUUID(), resourceId, base.plusSeconds(120), Map.of()));

        var byActor = repository.search(new AuditEventQuery("STAFF_USER", resourceId, actor, null, null),
                PageRequest.of(0, 10));
        var inWindow = repository.search(new AuditEventQuery(null, resourceId, null, base.plusSeconds(30),
                base.plusSeconds(120)), PageRequest.of(0, 10));

        assertThat(byActor.getTotalElements()).isEqualTo(2);
        assertThat(byActor.getContent().getFirst().occurredAt()).isEqualTo(base.plusSeconds(60));
        assertThat(byActor.getContent().get(1).details()).containsEntry("reason", "BAD_PASSWORD");
        assertThat(byActor.getContent().getFirst().details()).isEmpty();
        assertThat(inWindow.getContent()).extracting(AuditEvent::occurredAt).containsExactly(base.plusSeconds(60));
    }

    private static AuditEvent event(UUID actor, String resourceId, Instant at, Map<String, String> details) {
        return new AuditEvent(Ids.newId(), at, actor, Role.ADMIN, "LOGIN_FAILED", "STAFF_USER", resourceId,
                AuditOutcome.DENIED, "10.0.0.1", details);
    }
}
