package br.com.vagaviva.scheduling.adapter.in.web;

import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase;
import br.com.vagaviva.scheduling.application.port.in.RunAllocationUseCase.AllocationRunResult;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.PublishSlotsCommand;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotFilter;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotTime;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agenda", description = "Vagas das unidades executantes e motor de alocação (RF-19 a RF-21, RF-23)")
@RestController
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel ou unidade sem permissão", content = @Content(mediaType = "application/problem+json"))
class SlotController {

    private final SlotUseCases slots;
    private final RunAllocationUseCase allocation;
    private final CurrentUserProvider currentUser;

    SlotController(SlotUseCases slots, RunAllocationUseCase allocation, CurrentUserProvider currentUser) {
        this.slots = slots;
        this.allocation = allocation;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Publicar vagas (SCHEDULER da unidade, ADMIN)",
            description = "Lote de 1 a 200 horários de um profissional. Com ≥ 5 dias de antecedência a vaga entra na "
                    + "alocação automática (dispara logo após a publicação); entre 2 h e 5 dias vai para o encaixe.")
    @ApiResponse(responseCode = "201", description = "Vagas criadas")
    @ApiResponse(responseCode = "422", description = "Passado (SLOT_IN_PAST), < 2 h (SLOT_TOO_SOON), sobreposição (SLOT_OVERLAP), unidade não executante (INVALID_UNIT)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/slots")
    @PreAuthorize("hasAnyRole('SCHEDULER','ADMIN')")
    ResponseEntity<List<SlotResponse>> publish(@Valid @RequestBody PublishSlotsRequest request, HttpServletRequest http) {
        List<Slot> published = slots.publish(new PublishSlotsCommand(request.unitId(), request.specialtyId(),
                request.professionalName(), request.slots().stream()
                        .map(time -> new SlotTime(time.startAt(), time.durationMinutes())).toList(),
                currentUser.get(), http.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.CREATED).body(published.stream().map(SlotResponse::from).toList());
    }

    @Operation(summary = "Consultar vagas", description = "SCHEDULER vê só a própria unidade. Ordenado por horário.")
    @ApiResponse(responseCode = "200", description = "Página de vagas")
    @GetMapping("/api/v1/slots")
    @PreAuthorize("hasAnyRole('SCHEDULER','REGULATOR','ADMIN')")
    PageResponse<SlotResponse> list(@RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) UUID specialtyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) SlotStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(slots.list(new SlotFilter(unitId, specialtyId, from, to, status),
                PageRequest.of(page, size), currentUser.get()), SlotResponse::from);
    }

    @Operation(summary = "Cancelar vaga (SCHEDULER da unidade, ADMIN)",
            description = "Se houver paciente agendado, o agendamento vira CANCELLED_BY_UNIT e ele volta à fila na mesma posição.")
    @ApiResponse(responseCode = "200", description = "Vaga cancelada")
    @ApiResponse(responseCode = "409", description = "Vaga já usada, perdida ou cancelada (SLOT_INVALID_STATE)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/slots/{id}/cancel")
    @PreAuthorize("hasAnyRole('SCHEDULER','ADMIN')")
    SlotResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelSlotRequest request, HttpServletRequest http) {
        return SlotResponse.from(slots.cancel(id, request.reason(), currentUser.get(), http.getRemoteAddr()));
    }

    @Operation(summary = "Executar a alocação agora (ADMIN, SCHEDULER)",
            description = "A alocação roda sozinha a cada 5 minutos e logo após cada publicação de agenda.")
    @ApiResponse(responseCode = "200", description = "Resultado da execução")
    @PostMapping("/api/v1/allocation-runs")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER')")
    AllocationRunResult runAllocation() {
        return allocation.run();
    }

    record PublishSlotsRequest(
            @NotNull UUID unitId,
            @NotNull UUID specialtyId,
            @Schema(example = "Dra. Ana Cardoso") @NotBlank @Size(max = 120) String professionalName,
            @NotEmpty @Size(max = 200) List<@Valid @NotNull SlotTimeRequest> slots) {
    }

    record SlotTimeRequest(
            @Schema(example = "2026-10-20T11:00:00Z") @NotNull Instant startAt,
            @Schema(example = "30") @Min(5) @Max(240) int durationMinutes) {
    }

    record CancelSlotRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record SlotResponse(UUID id, UUID unitId, UUID specialtyId, String professionalName, Instant startAt,
            int durationMinutes, SlotStatus status) {

        static SlotResponse from(Slot slot) {
            return new SlotResponse(slot.id(), slot.unitId(), slot.specialtyId(), slot.professionalName(),
                    slot.startAt(), slot.durationMinutes(), slot.status());
        }
    }
}
