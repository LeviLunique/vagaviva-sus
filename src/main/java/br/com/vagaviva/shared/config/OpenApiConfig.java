package br.com.vagaviva.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * O servidor anunciado é a URL pública (CloudFront), não o gerado a partir do Host da
     * requisição: atrás da CloudFront/ALB esse Host é o endereço interno da AWS, e o
     * "Try it out" do Swagger chamaria um endereço inalcançável.
     */
    @Bean
    OpenAPI vagaVivaOpenApi(@Value("${vagaviva.api.version:v1}") String version,
                            @Value("${vagaviva.public-base-url}") String publicBaseUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("VagaViva API")
                        .version(version)
                        .description("Regulação ambulatorial do SUS com confirmação ativa e reaproveitamento automático de vagas."))
                .servers(List.of(new Server().url(publicBaseUrl)));
    }
}
