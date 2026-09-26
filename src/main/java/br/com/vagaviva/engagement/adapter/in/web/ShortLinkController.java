package br.com.vagaviva.engagement.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Link curto da mensagem ({@code /p/{token}}): cabe no SMS e leva ao recurso do agendamento. */
@Tag(name = "Ações do paciente (público)")
@RestController
@SecurityRequirements
class ShortLinkController {

    @Operation(summary = "Link curto do paciente", description = "Redireciona para /api/v1/patient-actions/{token}.")
    @ApiResponse(responseCode = "302", description = "Redirecionamento")
    @GetMapping("/p/{token}")
    ResponseEntity<Void> redirect(@PathVariable String token) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/api/v1/patient-actions/" + token)).build();
    }
}
