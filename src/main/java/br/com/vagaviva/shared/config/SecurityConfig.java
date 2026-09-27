package br.com.vagaviva.shared.config;

import br.com.vagaviva.shared.security.JwtClaims;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * API stateless protegida por JWT (OAuth2 Resource Server): nega por padrão, libera só as rotas
 * públicas e converte a claim {@code role} em {@code ROLE_<papel>} para o {@code @PreAuthorize}.
 * Erros 401/403 dos filtros são delegados ao {@code GlobalExceptionHandler} — mesmo formato
 * {@code problem+json} dos demais erros.
 *
 * <p>CSRF desligado de propósito: não há sessão nem cookie; o token vai no header
 * {@code Authorization} (ver docs/security.md).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ROUTES = {
        "/",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/api/v1/public/**",
        "/api/v1/patient-actions/**",
        "/p/**"
    };

    static final String LOGIN_ROUTE = "/api/v1/auth/login";

    /** A CSP restritiva vale para toda a API; a Swagger UI (fora de produção) precisa de scripts e estilos. */
    private static final RequestMatcher NOT_SWAGGER = new NegatedRequestMatcher(new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern("/swagger-ui/**"), PathPatternRequestMatcher.pathPattern("/v3/api-docs/**")));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        AccessDeniedHandler forbidden = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS fechado: a API não é chamada por páginas de outras origens (sem cabeçalhos CORS).
                .cors(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        // Links do paciente levam o token na URL: nunca repassar a URL como Referer.
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(NOT_SWAGGER,
                                new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_ROUTE).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
