package br.com.vagaviva.regulation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProtocolTest {

    @Test
    @DisplayName("RN-04: VV-<ano>-<sequencial de 7 dígitos com zeros à esquerda>")
    void shouldFormatProtocol() {
        assertThat(Protocol.of(2026, 123).value()).isEqualTo("VV-2026-0000123");
        assertThat(Protocol.of(2027, 12_345_678).value()).isEqualTo("VV-2027-12345678");
    }

    @Test
    @DisplayName("reconhece protocolo digitado pelo cidadão (minúsculas e espaços) e rejeita formato inválido")
    void shouldRecognizeTypedProtocol() {
        assertThat(Protocol.looksValid(" vv-2026-0000123 ")).isTrue();
        assertThat(Protocol.looksValid("VV-26-123")).isFalse();
        assertThat(Protocol.looksValid(null)).isFalse();
        assertThatThrownBy(() -> new Protocol("123")).isInstanceOf(IllegalArgumentException.class);
    }
}
