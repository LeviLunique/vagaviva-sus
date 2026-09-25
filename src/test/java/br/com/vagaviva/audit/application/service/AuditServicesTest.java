package br.com.vagaviva.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.audit.AuditEntry;
import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.application.port.in.AuditEventQuery;
import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import br.com.vagaviva.audit.domain.AuditEvent;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuditServicesTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Mock AuditEventRepository repository;

    @Test
    @DisplayName("registra o evento com id UUIDv7 e o instante do relógio")
    void shouldRecordEvent() {
        var service = new RecordAuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID actor = UUID.randomUUID();

        service.record(new AuditEntry(actor, Role.ADMIN, "STAFF_USER_CREATED", "STAFF_USER", "42",
                AuditOutcome.SUCCESS, "10.0.0.1", null));

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).append(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.id().version()).isEqualTo(7);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.actorId()).isEqualTo(actor);
        assertThat(event.details()).isEmpty();
    }

    @Test
    @DisplayName("ação, tipo de recurso e resultado são obrigatórios")
    void shouldRequireMandatoryFields() {
        assertThatThrownBy(() -> new AuditEntry(null, null, null, "X", null, AuditOutcome.SUCCESS, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("consulta delega ao repositório; período invertido ou vazio ⇒ INVALID_PERIOD (422)")
    void shouldValidatePeriod() {
        var service = new SearchAuditEventsService(repository);
        var valid = new AuditEventQuery(null, null, null, NOW.minusSeconds(60), NOW);
        var page = new PageImpl<AuditEvent>(List.of());
        when(repository.search(valid, PageRequest.of(0, 20))).thenReturn(page);

        assertThat(service.search(valid, PageRequest.of(0, 20))).isSameAs(page);
        assertThatThrownBy(() -> service.search(new AuditEventQuery(null, null, null, NOW, NOW), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
    }
}
