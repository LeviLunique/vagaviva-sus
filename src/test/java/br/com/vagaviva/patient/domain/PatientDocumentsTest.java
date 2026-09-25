package br.com.vagaviva.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PatientDocumentsTest {

    @Nested
    class CnsTest {

        @ParameterizedTest
        @ValueSource(strings = {"115881399860000", "298797309110002", "741707536455688", "815188447294054",
            "957616987168976", "957 6169 8716 8976"})
        @DisplayName("aceita CNS definitivos (1/2) e provisórios (7/8/9) com soma ponderada múltipla de 11")
        void shouldAcceptValidCns(String cns) {
            assertThat(Cns.of(cns).value()).hasSize(15).doesNotContain(" ");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"115881399860001", "315881399860000", "11588139986000", "1158813998600000",
            "11588139986000a", "000000000000000"})
        @DisplayName("rejeita CNS com dígito errado, prefixo inválido ou tamanho diferente de 15")
        void shouldRejectInvalidCns(String cns) {
            assertThatThrownBy(() -> Cns.of(cns))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo("INVALID_CNS");
        }

        @Test
        @DisplayName("RN-21: mascara mantendo só os 4 últimos dígitos")
        void shouldMask() {
            assertThat(Cns.of("115881399860000").masked()).isEqualTo("***********0000");
        }
    }

    @Nested
    class CpfTest {

        @ParameterizedTest
        @ValueSource(strings = {"68469788019", "932.081.967-09", "27317784826"})
        @DisplayName("aceita CPF com dígitos verificadores corretos (com ou sem pontuação)")
        void shouldAcceptValidCpf(String cpf) {
            assertThat(Cpf.of(cpf).value()).hasSize(11);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"68469788018", "68469788009", "11111111111", "1234567890", "6846978801a"})
        @DisplayName("rejeita CPF com dígito verificador errado, sequência repetida ou formato inválido")
        void shouldRejectInvalidCpf(String cpf) {
            assertThatThrownBy(() -> Cpf.of(cpf))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo("INVALID_CPF");
        }

        @Test
        @DisplayName("RN-21: mascara mantendo só os dígitos verificadores")
        void shouldMask() {
            assertThat(Cpf.of("68469788019").masked()).isEqualTo("***.***.***-19");
        }
    }

    @Nested
    class PhoneNumberTest {

        @ParameterizedTest
        @ValueSource(strings = {"+5511987654321", "+55 (21) 99876-5432", "+5599912345678"})
        @DisplayName("aceita celular brasileiro em E.164 (digitação com espaços, parênteses e hífen)")
        void shouldAcceptMobile(String phone) {
            assertThat(PhoneNumber.of(phone).value()).matches("\\+55\\d{11}");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"11987654321", "+551187654321", "+5511887654321", "+5501987654321",
            "+14155552671", "+55119876543210"})
        @DisplayName("rejeita número sem +55, fixo, DDD inválido, estrangeiro ou com tamanho errado")
        void shouldRejectInvalidPhone(String phone) {
            assertThatThrownBy(() -> PhoneNumber.of(phone))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo("INVALID_PHONE");
        }
    }
}
