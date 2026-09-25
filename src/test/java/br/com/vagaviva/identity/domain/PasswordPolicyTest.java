package br.com.vagaviva.identity.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Senha12345", "abcdefghi1", "1234567890a", "Çãoéíóú2026"})
    @DisplayName("RN-02: aceita senhas com 10+ caracteres contendo letras e números")
    void shouldAcceptStrongPasswords(String password) {
        assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Curta1", "SomenteLetras", "1234567890", "abc 12345"})
    @DisplayName("RN-02: rejeita senhas curtas, só letras, só números ou ausentes com WEAK_PASSWORD")
    void shouldRejectWeakPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("WEAK_PASSWORD");
    }

    @Test
    @DisplayName("rejeita senhas acima de 72 bytes (limite do BCrypt)")
    void shouldRejectPasswordsLongerThanBcryptLimit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a1".repeat(37)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("PASSWORD_TOO_LONG");
    }
}
