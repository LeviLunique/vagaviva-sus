package br.com.vagaviva.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    @DisplayName("toda exceção de domínio carrega um código estável e uma mensagem para o usuário")
    void shouldCarryCodeAndMessage() {
        DomainException ex = new ConflictException("EMAIL_ALREADY_REGISTERED", "E-mail já cadastrado.");

        assertThat(ex.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(ex).hasMessage("E-mail já cadastrado.");
    }

    @Test
    @DisplayName("as subclasses representam as famílias de erro da API")
    void shouldExposeAllFamilies() {
        assertThat(new NotFoundException("X", "m")).isInstanceOf(DomainException.class);
        assertThat(new BusinessRuleException("X", "m")).isInstanceOf(DomainException.class);
        assertThat(new GoneException("X", "m")).isInstanceOf(DomainException.class);
        assertThat(new ForbiddenOperationException("X", "m")).isInstanceOf(DomainException.class);
        assertThat(new UnauthenticatedException("X", "m")).isInstanceOf(DomainException.class);
    }
}
