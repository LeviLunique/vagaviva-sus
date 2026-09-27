package br.com.vagaviva.identity.adapter.in.web;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.aStaffUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.identity.application.port.in.ChangeStaffUserStatusUseCase;
import br.com.vagaviva.identity.application.port.in.CreateStaffUserUseCase;
import br.com.vagaviva.identity.application.port.in.GetStaffUserUseCase;
import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase;
import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StaffUserController.class)
@Import(WebSliceSecurity.class)
class StaffUserControllerTest {

    private static final String VALID_BODY = """
            {"name":"Regina Reguladora","email":"regina@vagaviva.test","password":"Senha12345","role":"REGULATOR"}""";

    @Autowired MockMvcTester mvc;
    @MockitoBean CreateStaffUserUseCase createStaffUser;
    @MockitoBean ListStaffUsersUseCase listStaffUsers;
    @MockitoBean GetStaffUserUseCase getStaffUser;
    @MockitoBean ChangeStaffUserStatusUseCase changeStaffUserStatus;

    @Test
    @DisplayName("ADMIN cadastra profissional ⇒ 201 com Location")
    void shouldCreateUser() {
        StaffUser user = aStaffUser().build();
        when(createStaffUser.create(any())).thenReturn(user);

        var result = mvc.post().uri("/api/v1/users").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/v1/users/" + user.id());
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(user.id().toString());
    }

    @Test
    @DisplayName("sem token ⇒ 401")
    void shouldRequireAuthentication() {
        assertThat(mvc.get().uri("/api/v1/users")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("qualquer papel diferente de ADMIN ⇒ 403 ACCESS_DENIED")
    void shouldForbidNonAdmins(Role role) {
        var result = mvc.post().uri("/api/v1/users").with(TestJwt.as(role))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        verifyNoInteractions(createStaffUser);
    }

    @Test
    @DisplayName("campos obrigatórios ausentes ⇒ 422 VALIDATION_FAILED")
    void shouldValidateRequest() {
        var result = mvc.post().uri("/api/v1/users").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("papel inexistente no JSON ⇒ 400 MALFORMED_REQUEST")
    void shouldRejectUnknownRole() {
        var result = mvc.post().uri("/api/v1/users").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY.replace("REGULATOR", "SUPERUSER"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("senha fraca ⇒ 422 WEAK_PASSWORD; e-mail duplicado ⇒ 409 EMAIL_ALREADY_REGISTERED")
    void shouldTranslateBusinessErrors() {
        when(createStaffUser.create(any()))
                .thenThrow(new BusinessRuleException("WEAK_PASSWORD", "fraca"))
                .thenThrow(new ConflictException("EMAIL_ALREADY_REGISTERED", "duplicado"));

        var weak = mvc.post().uri("/api/v1/users").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);
        var duplicated = mvc.post().uri("/api/v1/users").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);

        assertThat(weak).hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("WEAK_PASSWORD");
        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("lista com filtros de papel e status, paginada")
    void shouldListUsers() {
        when(listStaffUsers.list(eq(new StaffUserFilter(Role.REGULATOR, true)), eq(PageRequest.of(1, 5))))
                .thenReturn(new PageImpl<>(List.of(aStaffUser().build()), PageRequest.of(1, 5), 6));

        var result = mvc.get().uri("/api/v1/users?role=REGULATOR&active=true&page=1&size=5")
                .with(TestJwt.as(Role.ADMIN));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(6);
        assertThat(result).bodyJson().extractingPath("$.content[0].role").isEqualTo("REGULATOR");
    }

    @Test
    @DisplayName("tamanho de página acima de 100 ⇒ 422")
    void shouldLimitPageSize() {
        assertThat(mvc.get().uri("/api/v1/users?size=101").with(TestJwt.as(Role.ADMIN))).hasStatus(422);
    }

    @Test
    @DisplayName("consulta um profissional; inexistente ⇒ 404")
    void shouldGetUser() {
        StaffUser user = aStaffUser().build();
        UUID unknown = UUID.randomUUID();
        when(getStaffUser.get(user.id())).thenReturn(user);
        when(getStaffUser.get(unknown)).thenThrow(new NotFoundException("STAFF_USER_NOT_FOUND", "não encontrado"));

        assertThat(mvc.get().uri("/api/v1/users/{id}", user.id()).with(TestJwt.as(Role.ADMIN))).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/users/{id}", unknown).with(TestJwt.as(Role.ADMIN)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("altera o status passando o ADMIN autenticado como ator")
    void shouldChangeStatus() {
        StaffUser user = aStaffUser().inactive().build();
        UUID adminId = UUID.randomUUID();
        when(changeStaffUserStatus.change(any())).thenReturn(user);

        var result = mvc.patch().uri("/api/v1/users/{id}/status", user.id()).with(TestJwt.as(Role.ADMIN, adminId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.active").isEqualTo(false);
        verify(changeStaffUserStatus).change(org.mockito.ArgumentMatchers.argThat(command ->
                command.actor().id().equals(adminId) && !command.active() && command.userId().equals(user.id())));
    }

    @Test
    @DisplayName("status sem o campo active ⇒ 422")
    void shouldRequireActiveFlag() {
        var result = mvc.patch().uri("/api/v1/users/{id}/status", UUID.randomUUID()).with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{}");

        assertThat(result).hasStatus(422);
    }
}
