package br.com.vagaviva.catalog.adapter.in.web;

import br.com.vagaviva.catalog.HealthUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** DTOs de entrada de unidades de saúde. */
final class HealthUnitRequests {

    private HealthUnitRequests() {
    }

    record RegisterHealthUnitRequest(
            @Schema(example = "2077485") @NotBlank @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos") String cnes,
            @Schema(example = "UBS Vila Esperança") @NotBlank @Size(max = 160) String name,
            @NotNull HealthUnitType type,
            @Schema(example = "3550308") @NotBlank @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (IBGE)") String municipalityCode,
            @Schema(example = "São Paulo") @NotBlank @Size(max = 80) String municipalityName,
            @Schema(example = "Rua da Esperança, 100") @NotBlank @Size(max = 200) String address,
            @Schema(description = "Municípios atendidos (IBGE); vazio ou ausente = atende todos", example = "[\"3550308\"]")
            @Size(max = 200) Set<@Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (IBGE)") String> serviceArea) {
    }

    record ServiceAreaRequest(
            @Schema(description = "Municípios atendidos (IBGE); vazio = atende todos", example = "[\"3550308\",\"3518800\"]")
            @NotNull @Size(max = 200) Set<@Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (IBGE)") String> serviceArea) {
    }
}
