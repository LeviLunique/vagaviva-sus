package br.com.vagaviva.identity.adapter.in.web;

import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase;
import br.com.vagaviva.identity.application.port.in.AuthenticateUseCase.LoginCommand;
import br.com.vagaviva.identity.application.port.in.GetStaffUserUseCase;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Login dos profissionais e perfil do usuário autenticado")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthenticateUseCase authenticate;
    private final GetStaffUserUseCase getStaffUser;
    private final CurrentUserProvider currentUser;

    AuthController(AuthenticateUseCase authenticate, GetStaffUserUseCase getStaffUser,
            CurrentUserProvider currentUser) {
        this.authenticate = authenticate;
        this.getStaffUser = getStaffUser;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Login do profissional",
            description = "Devolve um JWT (RS256, 60 min) para o header `Authorization: Bearer`. Após 5 falhas "
                    + "seguidas o usuário fica bloqueado por 15 minutos; a resposta de falha é sempre a mesma.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Autenticado")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas, usuário inativo ou bloqueado (resposta genérica)",
            content = @Content(mediaType = "application/problem+json", examples = @ExampleObject("""
                    {"type":"https://vagaviva.dev/problems/invalid-credentials","title":"Não autenticado","status":401,
                     "detail":"E-mail ou senha inválidos.","instance":"/api/v1/auth/login","code":"INVALID_CREDENTIALS"}""")))
    @ApiResponse(responseCode = "422", description = "E-mail ou senha ausentes/inválidos",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        var command = new LoginCommand(request.email(), request.password(), http.getRemoteAddr());
        return LoginResponse.from(authenticate.authenticate(command));
    }

    @Operation(summary = "Meu perfil", description = "Dados do profissional dono do token.")
    @ApiResponse(responseCode = "200", description = "Perfil do usuário autenticado")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido/expirado",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/me")
    StaffUserResponse me() {
        return StaffUserResponse.from(getStaffUser.get(currentUser.get().id()));
    }
}
