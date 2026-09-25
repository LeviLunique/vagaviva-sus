package br.com.vagaviva.audit.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.audit.application.port.in.SearchAuditEventsUseCase;
import br.com.vagaviva.audit.domain.AuditEvent;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AuditController.class)
@Import(WebSliceSecurity.class)
class AuditControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean SearchAuditEventsUseCase searchAuditEvents;

    @Test
    @DisplayName("ADMIN consulta a trilha com filtros; details vira objeto JSON")
    void shouldSearchWithFilters() {
        UUID actor = UUID.randomUUID();
        var event = new AuditEvent(UUID.randomUUID(), Instant.parse("2026-09-25T12:00:00Z"), actor, Role.ADMIN,
                "LOGIN_SUCCEEDED", "STAFF_USER", actor.toString(), AuditOutcome.SUCCESS, "10.0.0.1",
                Map.of("reason", "X"));
        when(searchAuditEvents.search(argThat(query -> "STAFF_USER".equals(query.resourceType())
                && actor.equals(query.actorId())
                && Instant.parse("2026-09-01T00:00:00Z").equals(query.from())), any()))
                .thenReturn(new PageImpl<>(List.of(event)));

        var result = mvc.get()
                .uri("/api/v1/audit-events?resourceType=STAFF_USER&actorId={a}&from=2026-09-01T00:00:00Z", actor)
                .with(TestJwt.as(Role.ADMIN));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[0].action").isEqualTo("LOGIN_SUCCEEDED");
        assertThat(result).bodyJson().extractingPath("$.content[0].details.reason").isEqualTo("X");
    }

    @Test
    @DisplayName("apenas ADMIN consulta a auditoria ⇒ 403 para MANAGER")
    void shouldForbidNonAdmins() {
        assertThat(mvc.get().uri("/api/v1/audit-events").with(TestJwt.as(Role.MANAGER)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("data em formato inválido ⇒ 400")
    void shouldRejectInvalidDate() {
        assertThat(mvc.get().uri("/api/v1/audit-events?from=ontem").with(TestJwt.as(Role.ADMIN)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
