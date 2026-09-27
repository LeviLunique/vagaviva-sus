package br.com.vagaviva.catalog.adapter.in.web;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.adapter.in.web.CatalogResponses.HealthUnitResponse;
import br.com.vagaviva.catalog.adapter.in.web.HealthUnitRequests.RegisterHealthUnitRequest;
import br.com.vagaviva.catalog.adapter.in.web.HealthUnitRequests.ServiceAreaRequest;
import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase;
import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase.HealthUnitFilter;
import br.com.vagaviva.catalog.application.port.in.RegisterHealthUnitUseCase;
import br.com.vagaviva.catalog.application.port.in.RegisterHealthUnitUseCase.RegisterHealthUnitCommand;
import br.com.vagaviva.catalog.application.port.in.ReplaceServiceAreaUseCase;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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

@Tag(name = "Unidades de saúde", description = "UBS solicitantes e unidades executantes (RF-06)")
@RestController
@RequestMapping("/api/v1/health-units")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
class HealthUnitController {

    private final RegisterHealthUnitUseCase registerHealthUnit;
    private final QueryHealthUnitsUseCase queryHealthUnits;
    private final ReplaceServiceAreaUseCase replaceServiceArea;

    HealthUnitController(RegisterHealthUnitUseCase registerHealthUnit, QueryHealthUnitsUseCase queryHealthUnits,
            ReplaceServiceAreaUseCase replaceServiceArea) {
        this.registerHealthUnit = registerHealthUnit;
        this.queryHealthUnits = queryHealthUnits;
        this.replaceServiceArea = replaceServiceArea;
    }

    @Operation(summary = "Cadastrar unidade de saúde (ADMIN)",
            description = "`PRIMARY_CARE` = UBS solicitante; `SPECIALIZED` = unidade executante. Área de atendimento vazia = atende todos os municípios.")
    @ApiResponse(responseCode = "201", description = "Criada; header Location aponta para o recurso")
    @ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "CNES já cadastrado (CNES_ALREADY_REGISTERED)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Campos inválidos", content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<HealthUnitResponse> register(@Valid @RequestBody RegisterHealthUnitRequest request) {
        HealthUnit unit = registerHealthUnit.register(new RegisterHealthUnitCommand(request.cnes(), request.name(),
                request.type(), request.municipalityCode(), request.municipalityName(), request.address(),
                request.serviceArea()));
        return ResponseEntity.created(URI.create("/api/v1/health-units/" + unit.id()))
                .body(HealthUnitResponse.from(unit));
    }

    @Operation(summary = "Listar unidades de saúde", description = "Filtros opcionais por tipo e município; ordenado por nome.")
    @ApiResponse(responseCode = "200", description = "Página de unidades")
    @ApiResponse(responseCode = "422", description = "Filtro ou paginação inválidos", content = @Content(mediaType = "application/problem+json"))
    @GetMapping
    PageResponse<HealthUnitResponse> list(@RequestParam(required = false) HealthUnitType type,
            @RequestParam(required = false) @Pattern(regexp = "\\d{7}") String municipalityCode,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(queryHealthUnits.list(new HealthUnitFilter(type, municipalityCode),
                PageRequest.of(page, size)), HealthUnitResponse::from);
    }

    @Operation(summary = "Consultar unidade de saúde")
    @ApiResponse(responseCode = "200", description = "Unidade")
    @ApiResponse(responseCode = "404", description = "Inexistente (HEALTH_UNIT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{id}")
    HealthUnitResponse get(@PathVariable UUID id) {
        return HealthUnitResponse.from(queryHealthUnits.get(id));
    }

    @Operation(summary = "Substituir a área de atendimento (ADMIN)",
            description = "Substitui a lista inteira de municípios atendidos. Lista vazia = atende todos.")
    @ApiResponse(responseCode = "200", description = "Área atualizada")
    @ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "404", description = "Inexistente (HEALTH_UNIT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Código de município inválido", content = @Content(mediaType = "application/problem+json"))
    @PutMapping("/{id}/service-area")
    @PreAuthorize("hasRole('ADMIN')")
    HealthUnitResponse replaceServiceArea(@PathVariable UUID id, @Valid @RequestBody ServiceAreaRequest request) {
        return HealthUnitResponse.from(replaceServiceArea.replace(id, request.serviceArea()));
    }
}
