package br.com.vagaviva.catalog.adapter.in.web;

import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aPrimaryCareUnit;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aSpecialty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase;
import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase.HealthUnitFilter;
import br.com.vagaviva.catalog.application.port.in.RegisterHealthUnitUseCase;
import br.com.vagaviva.catalog.application.port.in.ReplaceServiceAreaUseCase;
import br.com.vagaviva.catalog.application.port.in.SpecialtyUseCases;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.security.Role;
import br.com.vagaviva.support.TestJwt;
import br.com.vagaviva.support.WebSliceSecurity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({HealthUnitController.class, SpecialtyController.class})
@Import(WebSliceSecurity.class)
class CatalogControllersTest {

    private static final String UNIT_BODY = """
            {"cnes":"2077485","name":"UBS Vila Esperança","type":"PRIMARY_CARE","municipalityCode":"3550308",
             "municipalityName":"São Paulo","address":"Rua da Esperança, 100","serviceArea":["3550308"]}""";

    @Autowired MockMvcTester mvc;
    @MockitoBean RegisterHealthUnitUseCase registerHealthUnit;
    @MockitoBean QueryHealthUnitsUseCase queryHealthUnits;
    @MockitoBean ReplaceServiceAreaUseCase replaceServiceArea;
    @MockitoBean SpecialtyUseCases specialties;

    @Test
    @DisplayName("ADMIN cadastra unidade ⇒ 201 com Location")
    void shouldRegisterUnit() {
        HealthUnit unit = aPrimaryCareUnit();
        when(registerHealthUnit.register(any())).thenReturn(unit);

        var result = mvc.post().uri("/api/v1/health-units").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(UNIT_BODY);

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/v1/health-units/" + unit.id());
        assertThat(result).bodyJson().extractingPath("$.cnes").isEqualTo("1234567");
    }

    @Test
    @DisplayName("apenas ADMIN cadastra unidade ⇒ 403 para REQUESTER")
    void shouldForbidNonAdminRegistration() {
        var result = mvc.post().uri("/api/v1/health-units").with(TestJwt.as(Role.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content(UNIT_BODY);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(registerHealthUnit);
    }

    @Test
    @DisplayName("CNES com formato errado ⇒ 422 com o campo em errors; duplicado ⇒ 409")
    void shouldValidateUnit() {
        var invalid = mvc.post().uri("/api/v1/health-units").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(UNIT_BODY.replace("2077485", "12"));
        assertThat(invalid).hasStatus(422).bodyJson().extractingPath("$.errors[0].field").isEqualTo("cnes");

        when(registerHealthUnit.register(any()))
                .thenThrow(new ConflictException("CNES_ALREADY_REGISTERED", "duplicado"));
        assertThat(mvc.post().uri("/api/v1/health-units").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(UNIT_BODY)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("qualquer profissional autenticado lista e consulta unidades; sem token ⇒ 401")
    void shouldListAndGetUnits() {
        HealthUnit unit = aPrimaryCareUnit();
        when(queryHealthUnits.list(eq(new HealthUnitFilter(HealthUnitType.PRIMARY_CARE, "3550308")), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(unit)));
        when(queryHealthUnits.get(unit.id())).thenReturn(unit);

        assertThat(mvc.get().uri("/api/v1/health-units?type=PRIMARY_CARE&municipalityCode=3550308")
                .with(TestJwt.as(Role.SCHEDULER)))
                .hasStatusOk().bodyJson().extractingPath("$.content[0].type").isEqualTo("PRIMARY_CARE");
        assertThat(mvc.get().uri("/api/v1/health-units/{id}", unit.id()).with(TestJwt.as(Role.MANAGER))).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/health-units")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("filtro de município fora do formato ⇒ 422")
    void shouldValidateMunicipalityFilter() {
        assertThat(mvc.get().uri("/api/v1/health-units?municipalityCode=abc").with(TestJwt.as(Role.ADMIN)))
                .hasStatus(422);
    }

    @Test
    @DisplayName("ADMIN substitui a área de atendimento")
    void shouldReplaceServiceArea() {
        HealthUnit unit = aPrimaryCareUnit();
        when(replaceServiceArea.replace(unit.id(), Set.of("3518800"))).thenReturn(unit);

        var result = mvc.put().uri("/api/v1/health-units/{id}/service-area", unit.id()).with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"serviceArea\":[\"3518800\"]}");

        assertThat(result).hasStatusOk();
        verify(replaceServiceArea).replace(unit.id(), Set.of("3518800"));
    }

    @Test
    @DisplayName("ADMIN cadastra especialidade; sensitive ausente vira false")
    void shouldRegisterSpecialty() {
        Specialty specialty = aSpecialty();
        when(specialties.register(any())).thenReturn(specialty);

        var result = mvc.post().uri("/api/v1/specialties").with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"CARDIO\",\"name\":\"Cardiologia\",\"type\":\"CONSULTATION\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/v1/specialties/" + specialty.id());
        verify(specialties).register(org.mockito.ArgumentMatchers.argThat(command -> !command.sensitive()));
    }

    @Test
    @DisplayName("lista (com filtro de tipo) e consulta especialidades")
    void shouldListSpecialties() {
        Specialty specialty = aSpecialty();
        when(specialties.list(SpecialtyType.CONSULTATION)).thenReturn(List.of(specialty));
        when(specialties.get(specialty.id())).thenReturn(specialty);

        assertThat(mvc.get().uri("/api/v1/specialties?type=CONSULTATION").with(TestJwt.as(Role.REGULATOR)))
                .hasStatusOk().bodyJson().extractingPath("$[0].code").isEqualTo("CARDIO");
        assertThat(mvc.get().uri("/api/v1/specialties/{id}", specialty.id()).with(TestJwt.as(Role.REGULATOR)))
                .hasStatusOk();
    }
}
