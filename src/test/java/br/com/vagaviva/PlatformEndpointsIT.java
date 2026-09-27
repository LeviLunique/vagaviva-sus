package br.com.vagaviva;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class PlatformEndpointsIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("health check é público e informa UP")
    void healthIsPublicAndUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("contrato OpenAPI é publicado sem autenticação")
    void openApiIsPublic() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.info.title").isEqualTo("VagaViva API");
    }

    @Test
    @DisplayName("a raiz é pública e devolve um índice JSON da API com links de descoberta")
    void rootReturnsApiIndex() {
        assertThat(mvc.get().uri("/"))
                .hasStatusOk()
                .hasContentType("application/json")
                .bodyJson()
                .extractingPath("$.name").isEqualTo("VagaViva API");
        assertThat(mvc.get().uri("/"))
                .bodyJson()
                .extractingPath("$.links.health").isEqualTo("/actuator/health");
        assertThat(mvc.get().uri("/"))
                .bodyJson()
                .extractingPath("$.links.docs").isEqualTo("/swagger-ui.html");
    }

    @Test
    @DisplayName("o contrato OpenAPI anuncia a URL pública, não o endereço interno do servidor")
    void openApiAdvertisesPublicBaseUrl() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.servers[0].url").isEqualTo("https://vagaviva.test");
    }

    @Test
    @DisplayName("qualquer rota de negócio exige autenticação (deny by default)")
    void businessRoutesRequireAuthentication() {
        assertThat(mvc.get().uri("/api/v1/referrals")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
