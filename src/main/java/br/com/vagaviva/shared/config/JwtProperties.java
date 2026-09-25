package br.com.vagaviva.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code vagaviva.security.jwt}: emissor, validade do token e chave privada RSA (Base64 de um
 * PEM PKCS#8, vinda de {@code JWT_PRIVATE_KEY}).
 */
@Validated
@ConfigurationProperties("vagaviva.security.jwt")
public record JwtProperties(@NotBlank String issuer, @NotNull Duration ttl, @Nullable String privateKey) {
}
