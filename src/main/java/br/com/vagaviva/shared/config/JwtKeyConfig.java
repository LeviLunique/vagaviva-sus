package br.com.vagaviva.shared.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Chave RS256 da API: a mesma aplicação emite (login) e valida (resource server) os tokens.
 * Na AWS a chave vem do Secrets Manager e é obrigatória — várias instâncias precisam validar os
 * tokens umas das outras. Localmente, sem chave, gera um par efêmero (tokens morrem no restart).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public RSAKey jwtSigningKey(JwtProperties properties, Environment environment) {
        String privateKey = properties.privateKey();
        if (privateKey != null && !privateKey.isBlank()) {
            return RsaKeys.fromPkcs8(privateKey);
        }
        if (environment.matchesProfiles("aws")) {
            throw new IllegalStateException("JWT_PRIVATE_KEY é obrigatória no perfil aws.");
        }
        log.warn("JWT_PRIVATE_KEY ausente: usando par de chaves efêmero (apenas desenvolvimento/testes).");
        return RsaKeys.generate();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
