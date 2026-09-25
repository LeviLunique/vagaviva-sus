package br.com.vagaviva.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * O servidor anunciado é a URL pública (CloudFront), não o gerado a partir do Host da
     * requisição: atrás da CloudFront/ALB esse Host é o endereço interno da AWS, e o
     * "Try it out" do Swagger chamaria um endereço inalcançável.
     *
     * <p>Todas as operações exigem o JWT ({@code bearerAuth}) por padrão; as públicas declaram
     * {@code @SecurityRequirements()} vazio.
     */
    @Bean
    OpenAPI vagaVivaOpenApi(@Value("${vagaviva.api.version:v1}") String version,
                            @Value("${vagaviva.public-base-url}") String publicBaseUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("VagaViva API")
                        .version(version)
                        .description("Regulação ambulatorial do SUS com confirmação ativa e reaproveitamento automático de vagas."))
                .servers(List.of(new Server().url(publicBaseUrl)))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token obtido em POST /api/v1/auth/login (validade de 60 minutos).")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
