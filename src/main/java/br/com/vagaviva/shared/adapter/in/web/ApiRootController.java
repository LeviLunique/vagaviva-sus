package br.com.vagaviva.shared.adapter.in.web;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raiz da API: documento de descoberta (nome, versão e links), não uma página. Os links de
 * documentação só aparecem onde o Swagger está habilitado — desligado em produção.
 */
@Hidden
@RestController
public class ApiRootController {

    private final String version;
    private final boolean docsEnabled;

    public ApiRootController(@Value("${vagaviva.api.version:v1}") String version,
                             @Value("${springdoc.swagger-ui.enabled:true}") boolean docsEnabled) {
        this.version = version;
        this.docsEnabled = docsEnabled;
    }

    @GetMapping(path = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiIndex index() {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("health", "/actuator/health");
        if (docsEnabled) {
            links.put("docs", "/swagger-ui.html");
            links.put("openapi", "/v3/api-docs");
        }
        return new ApiIndex("VagaViva API", version,
                "Regulação ambulatorial do SUS com confirmação ativa e reaproveitamento automático de vagas.",
                links);
    }

    public record ApiIndex(String name, String version, String description, Map<String, String> links) {
    }
}
