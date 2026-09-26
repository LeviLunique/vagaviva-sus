package br.com.vagaviva.scheduling.adapter.in.web;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.UNIT;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.aPendingAppointment;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.anAvailableSlot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase.AllocationRunResult;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({SlotController.class, AppointmentController.class})
@Import(WebSliceSecurity.class)
class SchedulingControllersTest {

    private static final String PUBLISH = """
            {"unitId":"%s","specialtyId":"%s","professionalName":"Dra. Ana",
             "slots":[{"startAt":"2026-10-20T11:00:00Z","durationMinutes":30}]}""";

    @Autowired MockMvcTester mvc;
    @MockitoBean SlotUseCases slots;
    @MockitoBean RunAllocationUseCase allocation;
    @MockitoBean AppointmentUseCases appointments;

    @Test
    @DisplayName("SCHEDULER publica vagas ⇒ 201 com a lista criada")
    void shouldPublishSlots() {
        Slot slot = anAvailableSlot();
        when(slots.publish(any())).thenReturn(List.of(slot));

        var result = mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UNIT))
                .contentType(MediaType.APPLICATION_JSON).content(PUBLISH.formatted(UNIT, UUID.randomUUID())).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("AVAILABLE");
        verify(slots).publish(argThat(command -> command.slots().size() == 1 && command.actor().unitId().equals(UNIT)));
    }

    @Test
    @DisplayName("lote vazio ou duração inválida ⇒ 422; REQUESTER publicando ⇒ 403; regra de negócio ⇒ 422")
    void shouldValidatePublication() {
        assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"unitId\":\"%s\",\"specialtyId\":\"%s\",\"professionalName\":\"X\",\"slots\":[]}"
                        .formatted(UNIT, UUID.randomUUID()))).hasStatus(422);
        assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(PUBLISH.formatted(UNIT, UUID.randomUUID()).replace("30", "3"))).hasStatus(422);
        assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.REQUESTER)).contentType(MediaType.APPLICATION_JSON)
                .content(PUBLISH.formatted(UNIT, UUID.randomUUID()))).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(slots);

        when(slots.publish(any())).thenThrow(new BusinessRuleException("SLOT_IN_PAST", "passado"));
        assertThat(mvc.post().uri("/api/v1/slots").with(TestJwt.as(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(PUBLISH.formatted(UNIT, UUID.randomUUID())))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("SLOT_IN_PAST");
    }

    @Test
    @DisplayName("listar e cancelar vagas; disparar alocação (ADMIN/SCHEDULER, não REGULATOR)")
    void shouldListCancelAndAllocate() {
        Slot slot = anAvailableSlot();
        when(slots.list(any(), eq(PageRequest.of(0, 20)), any())).thenReturn(new PageImpl<>(List.of(slot)));
        when(slots.cancel(eq(slot.id()), eq("Reforma"), any(), any())).thenReturn(slot);
        when(allocation.run()).thenReturn(new AllocationRunResult(3, 2, 1));

        assertThat(mvc.get().uri("/api/v1/slots?status=AVAILABLE").with(TestJwt.as(Role.REGULATOR))).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/slots/{id}/cancel", slot.id()).with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reforma\"}")).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/allocation-runs").with(TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UNIT)))
                .hasStatusOk().bodyJson().extractingPath("$.allocated").isEqualTo(2);
        assertThat(mvc.post().uri("/api/v1/allocation-runs").with(TestJwt.as(Role.REGULATOR)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("agendamentos: consultar, listar por data, check-in e falta só para SCHEDULER")
    void shouldServeAppointments() {
        Appointment appointment = aPendingAppointment();
        var scheduler = TestJwt.as(Role.SCHEDULER, UUID.randomUUID(), UNIT);
        when(appointments.get(eq(appointment.id()), any(), any())).thenReturn(appointment);
        when(appointments.list(eq(new AppointmentFilter(null, LocalDate.of(2026, 10, 5), AppointmentStatus.PENDING_CONFIRMATION)),
                eq(PageRequest.of(0, 20)), any())).thenReturn(new PageImpl<>(List.of()));
        when(appointments.checkIn(eq(appointment.id()), any(), any())).thenReturn(appointment);
        when(appointments.markNoShow(eq(appointment.id()), any(), any()))
                .thenThrow(new BusinessRuleException("NO_SHOW_BEFORE_START", "antes"));

        assertThat(mvc.get().uri("/api/v1/appointments/{id}", appointment.id()).with(TestJwt.as(Role.REGULATOR)))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PENDING_CONFIRMATION");
        assertThat(mvc.get().uri("/api/v1/appointments?date=2026-10-05&status=PENDING_CONFIRMATION").with(scheduler))
                .hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/appointments/{id}/check-in", appointment.id()).with(scheduler)).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/appointments/{id}/no-show", appointment.id()).with(scheduler))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("NO_SHOW_BEFORE_START");
        assertThat(mvc.post().uri("/api/v1/appointments/{id}/check-in", appointment.id()).with(TestJwt.as(Role.ADMIN)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
