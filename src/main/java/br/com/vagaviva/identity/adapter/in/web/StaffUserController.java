package br.com.vagaviva.identity.adapter.in.web;

import br.com.vagaviva.identity.application.port.in.ChangeStaffUserStatusUseCase;
import br.com.vagaviva.identity.application.port.in.ChangeStaffUserStatusUseCase.ChangeStaffUserStatusCommand;
import br.com.vagaviva.identity.application.port.in.CreateStaffUserUseCase;
import br.com.vagaviva.identity.application.port.in.CreateStaffUserUseCase.CreateStaffUserCommand;
import br.com.vagaviva.identity.application.port.in.GetStaffUserUseCase;
import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase;
import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import br.com.vagaviva.shared.security.Role;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profissionais", description = "Gestão dos usuários do sistema (somente ADMIN)")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel diferente de ADMIN", content = @Content(mediaType = "application/problem+json"))
class StaffUserController {

    private final CreateStaffUserUseCase createStaffUser;
    private final ListStaffUsersUseCase listStaffUsers;
    private final GetStaffUserUseCase getStaffUser;
    private final ChangeStaffUserStatusUseCase changeStaffUserStatus;
    private final CurrentUserProvider currentUser;

    StaffUserController(CreateStaffUserUseCase createStaffUser, ListStaffUsersUseCase listStaffUsers,
            GetStaffUserUseCase getStaffUser, ChangeStaffUserStatusUseCase changeStaffUserStatus,
            CurrentUserProvider currentUser) {
        this.createStaffUser = createStaffUser;
        this.listStaffUsers = listStaffUsers;
        this.getStaffUser = getStaffUser;
        this.changeStaffUserStatus = changeStaffUserStatus;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Cadastrar profissional",
            description = "REQUESTER e SCHEDULER exigem `healthUnitId`. Senha com no mínimo 10 caracteres, letras e números.")
    @ApiResponse(responseCode = "201", description = "Criado; header Location aponta para o recurso")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado (EMAIL_ALREADY_REGISTERED)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Senha fraca (WEAK_PASSWORD), unidade ausente (HEALTH_UNIT_REQUIRED) ou campos inválidos",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    ResponseEntity<StaffUserResponse> create(@Valid @RequestBody CreateStaffUserRequest request,
            HttpServletRequest http) {
        StaffUser user = createStaffUser.create(new CreateStaffUserCommand(request.name(), request.email(),
                request.password(), request.role(), request.healthUnitId(), currentUser.get(), http.getRemoteAddr()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(StaffUserResponse.from(user));
    }

    @Operation(summary = "Listar profissionais", description = "Filtros opcionais por papel e status; ordenado por nome.")
    @ApiResponse(responseCode = "200", description = "Página de profissionais")
    @ApiResponse(responseCode = "422", description = "Paginação inválida", content = @Content(mediaType = "application/problem+json"))
    @GetMapping
    PageResponse<StaffUserResponse> list(@RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(listStaffUsers.list(new StaffUserFilter(role, active), PageRequest.of(page, size)),
                StaffUserResponse::from);
    }

    @Operation(summary = "Consultar profissional")
    @ApiResponse(responseCode = "200", description = "Profissional")
    @ApiResponse(responseCode = "404", description = "Inexistente (STAFF_USER_NOT_FOUND)",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{id}")
    StaffUserResponse get(@PathVariable UUID id) {
        return StaffUserResponse.from(getStaffUser.get(id));
    }

    @Operation(summary = "Ativar ou desativar profissional",
            description = "Usuário inativo não consegue fazer login. O ADMIN não pode desativar a si mesmo.")
    @ApiResponse(responseCode = "200", description = "Status alterado")
    @ApiResponse(responseCode = "404", description = "Inexistente (STAFF_USER_NOT_FOUND)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "Tentativa de desativar a si mesmo (CANNOT_DEACTIVATE_SELF)",
            content = @Content(mediaType = "application/problem+json"))
    @PatchMapping("/{id}/status")
    StaffUserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStaffUserStatusRequest request,
            HttpServletRequest http) {
        var command = new ChangeStaffUserStatusCommand(id, request.active(), currentUser.get(), http.getRemoteAddr());
        return StaffUserResponse.from(changeStaffUserStatus.change(command));
    }
}
