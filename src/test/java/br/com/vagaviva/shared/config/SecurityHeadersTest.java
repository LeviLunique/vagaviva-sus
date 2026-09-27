package br.com.vagaviva.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.support.WebSliceSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** F8 (OWASP A05): cabeçalhos de segurança e CORS fechado em qualquer resposta da API. */
@WebMvcTest(SecurityHeadersTest.Probe.class)
@Import({WebSliceSecurity.class, SecurityHeadersTest.Probe.class})
class SecurityHeadersTest {

    @Autowired MockMvcTester mvc;

    @Test
    @DisplayName("links do paciente não vazam no Referer; sem cache, sniffing, frames nem recursos externos")
    void shouldSendSecurityHeaders() {
        MvcTestResult result = mvc.get().uri("/api/v1/public/probe").exchange();

        var response = result.getResponse();
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(result.getResponse().getHeader("Permissions-Policy")).contains("geolocation=()");
    }

    @Test
    @DisplayName("Swagger UI (fora de produção) fica sem a CSP restritiva, que bloquearia os scripts dela")
    void swaggerKeepsWorking() {
        assertThat(mvc.get().uri("/swagger-ui/probe").exchange().getResponse()
                .getHeader("Content-Security-Policy")).isNull();
    }

    @Test
    @DisplayName("CORS fechado: requisição de outra origem não recebe Access-Control-Allow-Origin")
    void shouldNotAllowCrossOrigin() {
        MvcTestResult preflight = mvc.options().uri("/api/v1/auth/login").header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange();
        MvcTestResult simple = mvc.get().uri("/api/v1/public/probe").header(HttpHeaders.ORIGIN, "https://evil.example")
                .exchange();

        assertThat(preflight.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(simple.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(preflight.getResponse().getStatus()).isNotEqualTo(HttpStatus.OK.value());
    }

    @RestController
    static class Probe {

        @GetMapping({"/api/v1/public/probe", "/swagger-ui/probe"})
        String probe() {
            return "ok";
        }
    }
}
