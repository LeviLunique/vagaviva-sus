package br.com.vagaviva.catalog.application.service;

import static br.com.vagaviva.catalog.fixtures.CatalogFixture.CLOCK;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.GUARULHOS;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.SAO_PAULO;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aPrimaryCareUnit;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aSpecialty;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aSpecializedUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase.HealthUnitFilter;
import br.com.vagaviva.catalog.application.port.in.RegisterHealthUnitUseCase.RegisterHealthUnitCommand;
import br.com.vagaviva.catalog.application.port.in.SpecialtyUseCases.RegisterSpecialtyCommand;
import br.com.vagaviva.catalog.application.port.out.HealthUnitRepository;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CatalogServicesTest {

    @Mock HealthUnitRepository units;
    @Mock SpecialtyRepository specialties;

    private HealthUnitService unitService() {
        return new HealthUnitService(units, CLOCK);
    }

    @Test
    @DisplayName("RF-06: cadastra unidade com área de atendimento convertida em códigos IBGE")
    void shouldRegisterUnit() {
        when(units.save(any())).thenAnswer(call -> call.getArgument(0));

        HealthUnit unit = unitService().register(new RegisterHealthUnitCommand("2077485", "AME Norte",
                HealthUnitType.SPECIALIZED, "3550308", "São Paulo", "Av. Norte, 1", Set.of("3550308", "3518800")));

        assertThat(unit.cnes().value()).isEqualTo("2077485");
        assertThat(unit.serviceArea()).containsExactlyInAnyOrder(SAO_PAULO, GUARULHOS);
    }

    @Test
    @DisplayName("área de atendimento ausente = atende todos")
    void shouldAcceptMissingServiceArea() {
        when(units.save(any())).thenAnswer(call -> call.getArgument(0));

        HealthUnit unit = unitService().register(new RegisterHealthUnitCommand("2077485", "UBS", HealthUnitType.PRIMARY_CARE,
                "3550308", "São Paulo", "Rua 1", null));

        assertThat(unit.serviceArea()).isEmpty();
    }

    @Test
    @DisplayName("CNES já cadastrado ⇒ CNES_ALREADY_REGISTERED (409)")
    void shouldRejectDuplicatedCnes() {
        when(units.existsByCnes(Cnes.of("2077485"))).thenReturn(true);

        assertThatThrownBy(() -> unitService().register(new RegisterHealthUnitCommand("2077485", "UBS",
                HealthUnitType.PRIMARY_CARE, "3550308", "São Paulo", "Rua 1", Set.of())))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("CNES_ALREADY_REGISTERED");
        verify(units, never()).save(any());
    }

    @Test
    @DisplayName("município inválido na área de atendimento ⇒ INVALID_MUNICIPALITY_CODE (422)")
    void shouldRejectInvalidServiceArea() {
        assertThatThrownBy(() -> unitService().register(new RegisterHealthUnitCommand("2077485", "UBS",
                HealthUnitType.PRIMARY_CARE, "3550308", "São Paulo", "Rua 1", Set.of("0000000"))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_MUNICIPALITY_CODE");
    }

    @Test
    @DisplayName("substitui a área de atendimento; unidade inexistente ⇒ HEALTH_UNIT_NOT_FOUND")
    void shouldReplaceServiceArea() {
        HealthUnit unit = aSpecializedUnit(Set.of(SAO_PAULO));
        when(units.findById(unit.id())).thenReturn(Optional.of(unit));
        when(units.save(unit)).thenReturn(unit);

        assertThat(unitService().replace(unit.id(), Set.of("3518800")).serviceArea()).containsExactly(GUARULHOS);
        assertThatThrownBy(() -> unitService().replace(UUID.randomUUID(), Set.of()))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("HEALTH_UNIT_NOT_FOUND");
    }

    @Test
    @DisplayName("lista unidades delegando filtro e paginação")
    void shouldListUnits() {
        var filter = new HealthUnitFilter(HealthUnitType.PRIMARY_CARE, null);
        var page = new PageImpl<>(List.of(aPrimaryCareUnit()));
        when(units.search(filter, PageRequest.of(0, 20))).thenReturn(page);

        assertThat(unitService().list(filter, PageRequest.of(0, 20))).isSameAs(page);
    }

    @Test
    @DisplayName("RF-07: cadastra especialidade; código repetido ⇒ SPECIALTY_CODE_ALREADY_REGISTERED")
    void shouldRegisterSpecialty() {
        var service = new SpecialtyService(specialties, CLOCK);
        when(specialties.existsByCode("PSIQ")).thenReturn(false, true);
        when(specialties.save(any())).thenAnswer(call -> call.getArgument(0));
        var command = new RegisterSpecialtyCommand("psiq", "Psiquiatria", SpecialtyType.CONSULTATION, true);

        assertThat(service.register(command).sensitive()).isTrue();
        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SPECIALTY_CODE_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("consulta e lista especialidades; inexistente ⇒ SPECIALTY_NOT_FOUND")
    void shouldQuerySpecialties() {
        var service = new SpecialtyService(specialties, CLOCK);
        Specialty specialty = aSpecialty();
        when(specialties.findById(specialty.id())).thenReturn(Optional.of(specialty));
        when(specialties.findAll(SpecialtyType.CONSULTATION)).thenReturn(List.of(specialty));

        assertThat(service.get(specialty.id())).isEqualTo(specialty);
        assertThat(service.list(SpecialtyType.CONSULTATION)).containsExactly(specialty);
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("SPECIALTY_NOT_FOUND");
    }

    @Test
    @DisplayName("CatalogApi expõe resumos e responde se a unidade atende o município")
    void shouldExposeCatalogApi() {
        var api = new CatalogApiImpl(units, specialties);
        HealthUnit unit = aSpecializedUnit(Set.of(SAO_PAULO));
        Specialty specialty = aSpecialty();
        when(units.findById(unit.id())).thenReturn(Optional.of(unit));
        when(specialties.findById(specialty.id())).thenReturn(Optional.of(specialty));

        assertThat(api.findUnit(unit.id())).hasValueSatisfying(summary -> {
            assertThat(summary.type()).isEqualTo(HealthUnitType.SPECIALIZED);
            assertThat(summary.cnes()).isEqualTo("7654321");
            assertThat(summary.serviceArea()).containsExactly("3550308");
        });
        assertThat(api.findSpecialty(specialty.id())).hasValueSatisfying(s -> assertThat(s.code()).isEqualTo("CARDIO"));
        assertThat(api.unitServes(unit.id(), "3550308")).isTrue();
        assertThat(api.unitServes(unit.id(), "3518800")).isFalse();
        assertThat(api.unitServes(UUID.randomUUID(), "3550308")).isFalse();
    }
}
