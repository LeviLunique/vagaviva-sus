package br.com.vagaviva.catalog.adapter.out.persistence;

import static br.com.vagaviva.catalog.fixtures.CatalogFixture.CLOCK;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.GUARULHOS;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.SAO_PAULO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.catalog.application.port.in.QueryHealthUnitsUseCase.HealthUnitFilter;
import br.com.vagaviva.catalog.application.port.out.HealthUnitRepository;
import br.com.vagaviva.catalog.application.port.out.SpecialtyRepository;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.catalog.domain.Specialty;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import br.com.vagaviva.support.IntegrationTest;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class CatalogPersistenceAdapterIT {

    @Autowired HealthUnitRepository units;
    @Autowired SpecialtyRepository specialties;

    @Test
    @DisplayName("grava unidade com área de atendimento (char(7)) e relê igual; atualiza a área com versão")
    void shouldRoundTripUnitWithServiceArea() {
        HealthUnit saved = units.save(unit(randomCnes(), HealthUnitType.SPECIALIZED, Set.of(SAO_PAULO, GUARULHOS)));

        HealthUnit loaded = units.findById(saved.id()).orElseThrow();
        assertThat(loaded.serviceArea()).containsExactlyInAnyOrder(SAO_PAULO, GUARULHOS);
        assertThat(loaded.version()).isZero();

        loaded.replaceServiceArea(Set.of(GUARULHOS), CLOCK);
        HealthUnit updated = units.save(loaded);
        assertThat(units.findById(saved.id()).orElseThrow().serviceArea()).containsExactly(GUARULHOS);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("CNES é único: concorrência no cadastro vira CNES_ALREADY_REGISTERED")
    void shouldEnforceUniqueCnes() {
        String cnes = randomCnes();
        units.save(unit(cnes, HealthUnitType.PRIMARY_CARE, Set.of()));

        assertThat(units.existsByCnes(Cnes.of(cnes))).isTrue();
        assertThatThrownBy(() -> units.save(unit(cnes, HealthUnitType.PRIMARY_CARE, Set.of())))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("CNES_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("filtra unidades por tipo e município")
    void shouldFilterUnits() {
        MunicipalityCode campinas = MunicipalityCode.of("3509502");
        HealthUnit unit = units.save(HealthUnit.register(Cnes.of(randomCnes()), "AME Campinas", HealthUnitType.SPECIALIZED,
                campinas, "Campinas", "Rua A, 1", Set.of(), CLOCK));

        var page = units.search(new HealthUnitFilter(HealthUnitType.SPECIALIZED, "3509502"), PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(HealthUnit::id).contains(unit.id());
        assertThat(page.getContent()).allMatch(u -> u.type() == HealthUnitType.SPECIALIZED
                && u.municipalityCode().equals(campinas));
    }

    @Test
    @DisplayName("especialidade: código único, lista por tipo ordenada por nome")
    void shouldPersistSpecialties() {
        String code = "EX" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        Specialty exam = specialties.save(Specialty.register(code, "Exame de teste", SpecialtyType.EXAM, false, CLOCK));

        assertThat(specialties.findById(exam.id())).contains(exam);
        assertThat(specialties.existsByCode(code)).isTrue();
        assertThat(specialties.findAll(SpecialtyType.EXAM)).extracting(Specialty::code).contains(code);
        assertThat(specialties.findAll(null)).extracting(Specialty::name).isSortedAccordingTo(String::compareTo);
        assertThatThrownBy(() -> specialties.save(Specialty.register(code, "Outra", SpecialtyType.EXAM, false, CLOCK)))
                .isInstanceOf(ConflictException.class);
    }

    private static HealthUnit unit(String cnes, HealthUnitType type, Set<MunicipalityCode> area) {
        return HealthUnit.register(Cnes.of(cnes), "Unidade " + cnes, type, SAO_PAULO, "São Paulo", "Rua B, 2", area, CLOCK);
    }

    static String randomCnes() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999));
    }
}
