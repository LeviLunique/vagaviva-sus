package br.com.vagaviva.identity.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    @DisplayName("RN-02: usa BCrypt com custo 12 por padrão")
    void shouldUseCost12ByDefault() {
        assertThat(new BCryptPasswordHasher().hash("Senha12345")).startsWith("$2a$12$");
    }

    @Test
    @DisplayName("hash confere com a senha original e não com outra")
    void shouldMatchOnlyTheOriginalPassword() {
        String hash = hasher.hash("Senha12345");

        assertThat(hash).doesNotContain("Senha12345");
        assertThat(hasher.matches("Senha12345", hash)).isTrue();
        assertThat(hasher.matches("Senha12346", hash)).isFalse();
    }

    @Test
    @DisplayName("senha acima de 72 bytes nunca confere")
    void shouldNotMatchOverlongPasswords() {
        String hash = hasher.hash("Senha12345");

        assertThat(hasher.matches("a1".repeat(40), hash)).isFalse();
    }
}
