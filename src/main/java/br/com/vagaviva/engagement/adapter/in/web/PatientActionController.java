package br.com.vagaviva.engagement.adapter.in.web;

import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.ActionResult;
import br.com.vagaviva.engagement.application.port.in.PatientActionUseCases.PatientAppointmentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ações do paciente (público)", description = "Link recebido por SMS/WhatsApp: ver, confirmar, cancelar ou desistir — sem login (RF-26)")
@RestController
@RequestMapping("/api/v1/patient-actions/{token}")
@SecurityRequirements
@ApiResponse(responseCode = "404", description = "Link inválido (PATIENT_LINK_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "410", description = "Link expirado (PATIENT_LINK_EXPIRED)", content = @Content(mediaType = "application/problem+json"))
class PatientActionController {

    private final PatientActionUseCases actions;

    PatientActionController(PatientActionUseCases actions) {
        this.actions = actions;
    }

    @Operation(summary = "Ver o agendamento", description = "Primeiro nome, rótulo genérico (consulta/exame), data, unidade, prazo e ações permitidas agora.")
    @ApiResponse(responseCode = "200", description = "Agendamento")
    @GetMapping
    PatientAppointmentView view(@PathVariable String token, HttpServletRequest http) {
        return actions.view(token, http.getRemoteAddr());
    }

    @Operation(summary = "Confirmar presença", description = "Até o prazo (RN-12). Repetir é aceito (idempotente).")
    @ApiResponse(responseCode = "200", description = "CONFIRMED")
    @ApiResponse(responseCode = "409", description = "Estado não permite (APPOINTMENT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Prazo vencido (CONFIRMATION_DEADLINE_PASSED)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/confirm")
    ActionResult confirm(@PathVariable String token, HttpServletRequest http) {
        return actions.confirm(token, http.getRemoteAddr());
    }

    @Operation(summary = "Não posso ir", description = "Até o início (RN-12): volta à fila na mesma posição e a vaga vai para outro paciente.")
    @ApiResponse(responseCode = "200", description = "CANCELLED_BY_PATIENT, backToQueue = true")
    @ApiResponse(responseCode = "409", description = "Estado não permite (APPOINTMENT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/cancel")
    ActionResult cancel(@PathVariable String token, HttpServletRequest http) {
        return actions.cancel(token, http.getRemoteAddr());
    }

    @Operation(summary = "Não preciso mais", description = "Sai da fila e a vaga vai para outro paciente.")
    @ApiResponse(responseCode = "200", description = "WITHDRAWN")
    @ApiResponse(responseCode = "409", description = "Estado não permite (APPOINTMENT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/withdraw")
    ActionResult withdraw(@PathVariable String token, HttpServletRequest http) {
        return actions.withdraw(token, http.getRemoteAddr());
    }
}
