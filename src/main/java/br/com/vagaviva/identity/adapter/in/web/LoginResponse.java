package br.com.vagaviva.identity.adapter.in.web;

import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase.AuthenticationResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade do token em segundos", example = "3600") long expiresIn,
        StaffUserResponse user) {

    static LoginResponse from(AuthenticationResult result) {
        return new LoginResponse(result.token().value(), "Bearer", result.token().expiresIn().toSeconds(),
                StaffUserResponse.from(result.user()));
    }
}
