package br.com.vagaviva.scheduling.adapter.in.web;

import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentItem;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agendamentos", description = "Agendamentos da unidade e registro de comparecimento (RF-21, RF-22)")
@RestController
@RequestMapping("/api/v1/appointments")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel ou unidade sem permissão", content = @Content(mediaType = "application/problem+json"))
class AppointmentController {

    private final AppointmentUseCases appointments;
    private final CurrentUserProvider currentUser;

    AppointmentController(AppointmentUseCases appointments, CurrentUserProvider currentUser) {
        this.appointments = appointments;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Consultar agendamento", description = "Leitura auditada; SCHEDULER só da própria unidade.")
    @ApiResponse(responseCode = "200", description = "Agendamento")
    @ApiResponse(responseCode = "404", description = "Inexistente (APPOINTMENT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHEDULER','REGULATOR','ADMIN')")
    AppointmentResponse get(@PathVariable UUID id, HttpServletRequest http) {
        return AppointmentResponse.from(appointments.get(id, currentUser.get(), http.getRemoteAddr()));
    }

    @Operation(summary = "Listar agendamentos", description = "Filtros por unidade, data (civil, São Paulo) e status; paciente por primeiro nome e CNS mascarado.")
    @ApiResponse(responseCode = "200", description = "Página de agendamentos, por horário")
    @GetMapping
    @PreAuthorize("hasAnyRole('SCHEDULER','REGULATOR','ADMIN')")
    PageResponse<AppointmentItem> list(@RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(appointments.list(new AppointmentFilter(unitId, date, status), PageRequest.of(page, size),
                currentUser.get()), item -> item);
    }

    @Operation(summary = "Registrar comparecimento (SCHEDULER da unidade)", description = "Somente no dia do atendimento (RN-19).")
    @ApiResponse(responseCode = "200", description = "ATTENDED")
    @ApiResponse(responseCode = "409", description = "Estado não permite (APPOINTMENT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Fora do dia (CHECK_IN_OUTSIDE_DAY)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('SCHEDULER')")
    AppointmentResponse checkIn(@PathVariable UUID id, HttpServletRequest http) {
        return AppointmentResponse.from(appointments.checkIn(id, currentUser.get(), http.getRemoteAddr()));
    }

    @Operation(summary = "Registrar falta (SCHEDULER da unidade)",
            description = "Somente após o início (RN-19); o encaminhamento volta para reavaliação do regulador (RN-14).")
    @ApiResponse(responseCode = "200", description = "NO_SHOW")
    @ApiResponse(responseCode = "409", description = "Estado não permite (APPOINTMENT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Antes do início (NO_SHOW_BEFORE_START)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('SCHEDULER')")
    AppointmentResponse noShow(@PathVariable UUID id, HttpServletRequest http) {
        return AppointmentResponse.from(appointments.markNoShow(id, currentUser.get(), http.getRemoteAddr()));
    }

    record AppointmentResponse(UUID id, UUID slotId, UUID referralId, UUID patientId, UUID unitId, UUID specialtyId,
            Instant startAt, AppointmentOrigin origin, AppointmentStatus status, Instant confirmationDeadline,
            Instant confirmedAt, Instant cancelledAt, Instant outcomeAt) {

        static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(a.id(), a.slotId(), a.referralId(), a.patientId(), a.unitId(),
                    a.specialtyId(), a.startAt(), a.origin(), a.status(), a.confirmationDeadline(), a.confirmedAt(),
                    a.cancelledAt(), a.outcomeAt());
        }
    }
}
