package br.com.vagaviva.catalog.domain;

import static br.com.vagaviva.catalog.fixtures.CatalogFixture.CLOCK;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.GUARULHOS;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.NOW;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.SAO_PAULO;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aPrimaryCareUnit;
import static br.com.vagaviva.catalog.fixtures.CatalogFixture.aSpecializedUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogDomainTest {

    @Test
    @DisplayName("unidade nova é ativa, com id UUIDv7 e textos sem espaços nas pontas")
    void shouldRegisterActiveUnit() {
        HealthUnit unit = aPrimaryCareUnit();

        assertThat(unit.isActive()).isTrue();
        assertThat(unit.id().version()).isEqualTo(7);
        assertThat(unit.createdAt()).isEqualTo(NOW);
        assertThat(unit.version()).isNull();
    }

    @Test
    @DisplayName("RF-06: área de atendimento vazia atende todos os municípios")
    void shouldServeEveryMunicipalityWhenServiceAreaIsEmpty() {
        assertThat(aPrimaryCareUnit().serves(GUARULHOS)).isTrue();
    }

    @Test
    @DisplayName("RF-06: com área definida, atende só os municípios listados; substituir a área vale por inteiro")
    void shouldServeOnlyListedMunicipalities() {
        HealthUnit unit = aSpecializedUnit(Set.of(SAO_PAULO));
        assertThat(unit.serves(SAO_PAULO)).isTrue();
        assertThat(unit.serves(GUARULHOS)).isFalse();

        Clock later = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
        unit.replaceServiceArea(Set.of(GUARULHOS), later);

        assertThat(unit.serviceArea()).containsExactly(GUARULHOS);
        assertThat(unit.serves(SAO_PAULO)).isFalse();
        assertThat(unit.updatedAt()).isEqualTo(later.instant());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123456", "12345678", "12345a7"})
    @DisplayName("CNES precisa ter exatamente 7 dígitos")
    void shouldRejectInvalidCnes(String cnes) {
        assertThatThrownBy(() -> Cnes.of(cnes))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_CNES");
    }

    @Test
    @DisplayName("especialidade normaliza o código em maiúsculas e guarda a marcação de sensível")
    void shouldRegisterSpecialty() {
        Specialty specialty = Specialty.register(" psiq ", " Psiquiatria ", SpecialtyType.CONSULTATION, true, CLOCK);

        assertThat(specialty.code()).isEqualTo("PSIQ");
        assertThat(specialty.name()).isEqualTo("Psiquiatria");
        assertThat(specialty.sensitive()).isTrue();
        assertThat(specialty.active()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "CÓDIGO", "com espaco", "ABCDEFGHIJKLMNOPQRSTU"})
    @DisplayName("código de especialidade fora do padrão ⇒ INVALID_SPECIALTY_CODE")
    void shouldRejectInvalidSpecialtyCode(String code) {
        assertThatThrownBy(() -> Specialty.register(code, "Nome", SpecialtyType.EXAM, false, CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_SPECIALTY_CODE");
    }
}
