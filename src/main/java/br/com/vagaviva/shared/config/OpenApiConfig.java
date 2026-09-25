package br.com.vagaviva.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI vagaVivaOpenApi(@Value("${vagaviva.api.version:v1}") String version) {
        return new OpenAPI().info(new Info()
                .title("VagaViva API")
                .version(version)
                .description("Regulação ambulatorial do SUS com confirmação ativa e reaproveitamento automático de vagas."));
    }
}
