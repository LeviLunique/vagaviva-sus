package br.com.vagaviva.identity.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "admin@vagaviva.local") @NotBlank @Email @Size(max = 160) String email,
        @Schema(example = "Admin@Local2026") @NotBlank @Size(max = 128) String password) {
}
