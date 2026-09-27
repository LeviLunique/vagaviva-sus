package br.com.vagaviva.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MunicipalityCodeTest {

    @Test
    @DisplayName("aceita o código IBGE de 7 dígitos (ex.: São Paulo 3550308)")
    void shouldAcceptSevenDigitIbgeCode() {
        assertThat(MunicipalityCode.of(" 3550308 ").value()).isEqualTo("3550308");
        assertThat(MunicipalityCode.of("3550308")).isEqualTo(MunicipalityCode.of("3550308"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"355030", "35503081", "35503O8", "0550308"})
    @DisplayName("rejeita códigos fora do formato IBGE com INVALID_MUNICIPALITY_CODE")
    void shouldRejectInvalidCodes(String code) {
        assertThatThrownBy(() -> MunicipalityCode.of(code))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_MUNICIPALITY_CODE");
    }
}
