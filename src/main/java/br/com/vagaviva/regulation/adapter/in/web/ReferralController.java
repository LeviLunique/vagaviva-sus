package br.com.vagaviva.regulation.adapter.in.web;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.CancelReferralRequest;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.CreateReferralRequest;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.CreatedReferralResponse;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.ReferralResponse;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.ReferralSummaryResponse;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.RegulationRequest;
import br.com.vagaviva.regulation.adapter.in.web.ReferralDtos.ResubmitReferralRequest;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.ReferralFilter;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.CreateReferralCommand;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.RegulateReferralCommand;
import br.com.vagaviva.regulation.application.port.in.ReferralCommandUseCases.ResubmitReferralCommand;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Encaminhamentos", description = "Encaminhamento pela UBS e regulação (RF-12 a RF-15)")
@RestController
@RequestMapping("/api/v1/referrals")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel ou unidade sem permissão", content = @Content(mediaType = "application/problem+json"))
class ReferralController {

    private final ReferralCommandUseCases commands;
    private final QueryReferralsUseCase queries;
    private final CurrentUserProvider currentUser;

    ReferralController(ReferralCommandUseCases commands, QueryReferralsUseCase queries, CurrentUserProvider currentUser) {
        this.commands = commands;
        this.queries = queries;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Registrar encaminhamento (REQUESTER)",
            description = "Gera o protocolo VV-AAAA-NNNNNNN e aguarda regulação. A unidade solicitante é a do usuário.")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "409", description = "Encaminhamento ativo para a mesma especialidade (REFERRAL_DUPLICATED)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Paciente/especialidade inválidos (INVALID_PATIENT, INVALID_SPECIALTY) ou CID inválido", content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    @PreAuthorize("hasRole('REQUESTER')")
    ResponseEntity<CreatedReferralResponse> create(@Valid @RequestBody CreateReferralRequest request,
            HttpServletRequest http) {
        Referral referral = commands.create(new CreateReferralCommand(request.patientId(), request.specialtyId(),
                request.clinicalJustification(), request.cid10(), Boolean.TRUE.equals(request.acceptsShortNotice()),
                currentUser.get(), http.getRemoteAddr()));
        return ResponseEntity.created(URI.create("/api/v1/referrals/" + referral.id()))
                .body(new CreatedReferralResponse(referral.id(), referral.protocol().value(), referral.status()));
    }

    @Operation(summary = "Consultar encaminhamento", description = "Leitura auditada; REQUESTER só vê os da própria unidade.")
    @ApiResponse(responseCode = "200", description = "Encaminhamento com dados clínicos")
    @ApiResponse(responseCode = "404", description = "Inexistente (REFERRAL_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('REQUESTER','REGULATOR','ADMIN')")
    ReferralResponse get(@PathVariable UUID id, HttpServletRequest http) {
        return ReferralResponse.from(queries.get(id, currentUser.get(), http.getRemoteAddr()));
    }

    @Operation(summary = "Listar encaminhamentos", description = "Mais recentes primeiro. REQUESTER vê só os da própria unidade.")
    @ApiResponse(responseCode = "200", description = "Página de encaminhamentos (sem dados clínicos)")
    @GetMapping
    @PreAuthorize("hasAnyRole('REQUESTER','REGULATOR','ADMIN')")
    PageResponse<ReferralSummaryResponse> list(@RequestParam(required = false) ReferralStatus status,
            @RequestParam(required = false) UUID specialtyId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(queries.list(new ReferralFilter(status, specialtyId, null), PageRequest.of(page, size),
                currentUser.get()), ReferralSummaryResponse::from);
    }

    @Operation(summary = "Reenviar encaminhamento devolvido (REQUESTER)", description = "RETURNED → PENDING_REGULATION com os dados corrigidos.")
    @ApiResponse(responseCode = "200", description = "Reenviado")
    @ApiResponse(responseCode = "409", description = "Não está devolvido (REFERRAL_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('REQUESTER')")
    ReferralResponse resubmit(@PathVariable UUID id, @Valid @RequestBody ResubmitReferralRequest request,
            HttpServletRequest http) {
        return ReferralResponse.from(commands.resubmit(new ResubmitReferralCommand(id, request.clinicalJustification(),
                request.cid10(), Boolean.TRUE.equals(request.acceptsShortNotice()), currentUser.get(),
                http.getRemoteAddr())));
    }

    @Operation(summary = "Regular encaminhamento (REGULATOR)",
            description = "APPROVE com classe de risco ⇒ entra na fila (WAITING); RETURN com justificativa ⇒ devolvido à UBS.")
    @ApiResponse(responseCode = "200", description = "Decisão registrada")
    @ApiResponse(responseCode = "409", description = "Não está aguardando regulação (REFERRAL_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Classe de risco ou justificativa ausente (RISK_CLASS_REQUIRED, RETURN_REASON_REQUIRED)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/{id}/regulation")
    @PreAuthorize("hasRole('REGULATOR')")
    ReferralResponse regulate(@PathVariable UUID id, @Valid @RequestBody RegulationRequest request,
            HttpServletRequest http) {
        return ReferralResponse.from(commands.regulate(new RegulateReferralCommand(id, request.decision(),
                request.riskClass(), request.justification(), currentUser.get(), http.getRemoteAddr())));
    }

    @Operation(summary = "Cancelar encaminhamento (REGULATOR, ADMIN)", description = "Cancelamento administrativo com motivo, antes do agendamento.")
    @ApiResponse(responseCode = "200", description = "Cancelado")
    @ApiResponse(responseCode = "409", description = "Estado não permite cancelar (REFERRAL_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('REGULATOR','ADMIN')")
    ReferralResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelReferralRequest request,
            HttpServletRequest http) {
        return ReferralResponse.from(commands.cancel(id, request.reason(), currentUser.get(), http.getRemoteAddr()));
    }
}
