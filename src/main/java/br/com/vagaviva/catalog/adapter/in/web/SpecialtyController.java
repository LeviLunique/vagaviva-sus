package br.com.vagaviva.catalog.adapter.in.web;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.adapter.in.web.CatalogResponses.SpecialtyResponse;
import br.com.vagaviva.catalog.application.port.in.SpecialtyUseCases;
import br.com.vagaviva.catalog.application.port.in.SpecialtyUseCases.RegisterSpecialtyCommand;
import br.com.vagaviva.catalog.domain.Specialty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Especialidades", description = "Especialidades e exames regulados (RF-07)")
@RestController
@RequestMapping("/api/v1/specialties")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
class SpecialtyController {

    private final SpecialtyUseCases specialties;

    SpecialtyController(SpecialtyUseCases specialties) {
        this.specialties = specialties;
    }

    @Operation(summary = "Cadastrar especialidade (ADMIN)",
            description = "`sensitive` = true (ex.: psiquiatria, infectologia) impede o nome nas mensagens ao paciente.")
    @ApiResponse(responseCode = "201", description = "Criada")
    @ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "Código já cadastrado (SPECIALTY_CODE_ALREADY_REGISTERED)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Campos inválidos", content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpecialtyResponse> register(@Valid @RequestBody RegisterSpecialtyRequest request) {
        Specialty specialty = specialties.register(new RegisterSpecialtyCommand(request.code(), request.name(),
                request.type(), Boolean.TRUE.equals(request.sensitive())));
        return ResponseEntity.created(URI.create("/api/v1/specialties/" + specialty.id()))
                .body(SpecialtyResponse.from(specialty));
    }

    @Operation(summary = "Listar especialidades", description = "Lista completa ordenada por nome; filtro opcional por tipo.")
    @ApiResponse(responseCode = "200", description = "Especialidades")
    @GetMapping
    List<SpecialtyResponse> list(@RequestParam(required = false) SpecialtyType type) {
        return specialties.list(type).stream().map(SpecialtyResponse::from).toList();
    }

    @Operation(summary = "Consultar especialidade")
    @ApiResponse(responseCode = "200", description = "Especialidade")
    @ApiResponse(responseCode = "404", description = "Inexistente (SPECIALTY_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{id}")
    SpecialtyResponse get(@PathVariable UUID id) {
        return SpecialtyResponse.from(specialties.get(id));
    }

    record RegisterSpecialtyRequest(
            @Schema(example = "CARDIO") @NotBlank @Size(max = 20) String code,
            @Schema(example = "Cardiologia") @NotBlank @Size(max = 120) String name,
            @NotNull SpecialtyType type,
            @Schema(description = "Especialidade sensível (padrão false)") Boolean sensitive) {
    }
}
