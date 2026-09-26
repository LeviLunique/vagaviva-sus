package br.com.vagaviva.scheduling.adapter.in.web;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.AppointmentView;
import br.com.vagaviva.scheduling.SchedulingApi;
import br.com.vagaviva.scheduling.application.port.in.ExpireConfirmationsUseCase;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = SchedulingDemoController.class, properties = "vagaviva.demo.enabled=true")
@Import(WebSliceSecurity.class)
class SchedulingDemoControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ExpireConfirmationsUseCase expirations;
    @MockitoBean SchedulingApi scheduling;

    @Test
    @DisplayName("RF-29: ADMIN expira o prazo agora ⇒ 200 EXPIRED_UNCONFIRMED; SCHEDULER ⇒ 403")
    void shouldExpireOnDemand() {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-20T11:00:00Z");
        when(scheduling.findAppointmentView(id)).thenReturn(Optional.of(new AppointmentView(id, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UNIT, UUID.randomUUID(), start, AppointmentOrigin.REGULAR,
                AppointmentStatus.EXPIRED_UNCONFIRMED, start)));

        assertThat(mvc.post().uri("/api/v1/dev/appointments/{id}/expire-confirmation", id).with(TestJwt.as(Role.ADMIN)))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("EXPIRED_UNCONFIRMED");
        verify(expirations).expireNow(id);

        UUID other = UUID.randomUUID();
        assertThat(mvc.post().uri("/api/v1/dev/appointments/{id}/expire-confirmation", other)
                .with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UNIT))).hasStatus(HttpStatus.FORBIDDEN);
        verify(expirations, never()).expireNow(other);
    }
}
