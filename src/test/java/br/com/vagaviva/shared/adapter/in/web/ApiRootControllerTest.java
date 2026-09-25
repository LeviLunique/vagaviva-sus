package br.com.vagaviva.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiRootControllerTest {

    @Test
    @DisplayName("com a documentação habilitada, o índice aponta para o Swagger e o contrato OpenAPI")
    void shouldAdvertiseDocsWhenEnabled() {
        var index = new ApiRootController("v1", true).index();

        assertThat(index.name()).isEqualTo("VagaViva API");
        assertThat(index.version()).isEqualTo("v1");
        assertThat(index.links())
                .containsEntry("health", "/actuator/health")
                .containsEntry("docs", "/swagger-ui.html")
                .containsEntry("openapi", "/v3/api-docs");
    }

    @Test
    @DisplayName("com a documentação desligada (produção), o índice não anuncia o Swagger")
    void shouldHideDocsWhenDisabled() {
        var index = new ApiRootController("v1", false).index();

        assertThat(index.links())
                .containsEntry("health", "/actuator/health")
                .doesNotContainKeys("docs", "openapi");
    }
}
