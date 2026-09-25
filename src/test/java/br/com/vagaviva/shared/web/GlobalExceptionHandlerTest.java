package br.com.vagaviva.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.GoneException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.domain.UnauthenticatedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build());

    @ParameterizedTest(name = "{0} ⇒ {1}")
    @CsvSource({
        "not-found, 404, Recurso não encontrado",
        "conflict, 409, Conflito",
        "business, 422, Regra de negócio violada",
        "gone, 410, Recurso expirado",
        "forbidden, 403, Acesso negado",
        "unauthenticated, 401, Não autenticado",
        "custom, 409, Conflito"
    })
    @DisplayName("exceções de domínio viram problem+json com o status da família e o código no type")
    void shouldMapDomainExceptions(String kind, int status, String title) {
        var result = mvc.get().uri("/probe/domain/{kind}", kind);

        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(title);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SAMPLE_CODE");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://vagaviva.dev/problems/sample-code");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Mensagem ao usuário.");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/probe/domain/" + kind);
    }

    @Test
    @DisplayName("corpo inválido (Bean Validation) ⇒ 422 com a lista de campos em errors")
    void shouldReturnValidationErrors() {
        var result = mvc.post().uri("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    @DisplayName("parâmetro fora do limite (validação de método) ⇒ 422 com o nome do parâmetro")
    void shouldReturnParameterValidationErrors() {
        var result = mvc.get().uri("/probe/page?size=500");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    @DisplayName("JSON malformado ⇒ 400 sem detalhes internos do parser")
    void shouldRejectMalformedJson() {
        var result = mvc.post().uri("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("parâmetro com tipo inválido (UUID malformado) ⇒ 400")
    void shouldRejectTypeMismatch() {
        var result = mvc.get().uri("/probe/items/not-a-uuid");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("id");
    }

    @Test
    @DisplayName("falha de autenticação ⇒ 401 com WWW-Authenticate Bearer")
    void shouldMapAuthenticationFailure() {
        var result = mvc.get().uri("/probe/authentication");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasHeader("WWW-Authenticate", "Bearer");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("papel sem permissão ⇒ 403 ACCESS_DENIED")
    void shouldMapAccessDenied() {
        assertThat(mvc.get().uri("/probe/access-denied"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("conflito de versão (optimistic locking) ⇒ 409 CONCURRENT_MODIFICATION")
    void shouldMapOptimisticLocking() {
        assertThat(mvc.get().uri("/probe/concurrent"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("erro inesperado ⇒ 500 genérico, sem a mensagem interna")
    void shouldHideUnexpectedErrors() {
        var result = mvc.get().uri("/probe/unexpected");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("segredo interno");
    }

    @Test
    @DisplayName("método HTTP não suportado ⇒ 405 no mesmo formato, com título em português")
    void shouldTranslateFrameworkErrors() {
        var result = mvc.delete().uri("/probe/unexpected");

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Método não suportado");
    }

    @Test
    @DisplayName("status sem código genérico específico cai em INTERNAL_ERROR ao montar o título")
    void shouldFallBackForUnknownStatus() {
        assertThat(ErrorCode.fromStatus(418)).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.NOT_FOUND.type()).hasToString("https://vagaviva.dev/problems/not-found");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/domain/{kind}")
        void domain(@PathVariable String kind) {
            String code = "SAMPLE_CODE";
            String message = "Mensagem ao usuário.";
            throw switch (kind) {
                case "not-found" -> new NotFoundException(code, message);
                case "conflict" -> new ConflictException(code, message);
                case "business" -> new BusinessRuleException(code, message);
                case "gone" -> new GoneException(code, message);
                case "forbidden" -> new ForbiddenOperationException(code, message);
                case "unauthenticated" -> new UnauthenticatedException(code, message);
                default -> new CustomConflict(code, message);
            };
        }

        @PostMapping("/probe/body")
        ProbeRequest body(@Valid @RequestBody ProbeRequest request) {
            return request;
        }

        @GetMapping("/probe/page")
        int page(@RequestParam @Max(100) int size) {
            return size;
        }

        @GetMapping("/probe/items/{id}")
        UUID item(@PathVariable UUID id) {
            return id;
        }

        @GetMapping("/probe/authentication")
        void authentication() {
            throw new BadCredentialsException("token inválido");
        }

        @GetMapping("/probe/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("negado");
        }

        @GetMapping("/probe/concurrent")
        void concurrent() {
            throw new OptimisticLockingFailureException("versão");
        }

        @GetMapping("/probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("segredo interno");
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }

    static class CustomConflict extends ConflictException {
        CustomConflict(String code, String message) {
            super(code, message);
        }
    }
}
