package br.com.vagaviva.identity.adapter.in.web;

import br.com.vagaviva.shared.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateStaffUserRequest(
        @Schema(example = "Regina Reguladora") @NotBlank @Size(max = 120) String name,
        @Schema(example = "regina@vagaviva.local") @NotBlank @Email @Size(max = 160) String email,
        @Schema(description = "Mínimo 10 caracteres, com letras e números", example = "Senha2026Forte")
        @NotBlank @Size(max = 128) String password,
        @NotNull Role role,
        @Schema(description = "Obrigatória para REQUESTER e SCHEDULER") UUID healthUnitId) {
}
