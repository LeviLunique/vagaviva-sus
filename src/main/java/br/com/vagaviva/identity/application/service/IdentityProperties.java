package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.domain.LockoutPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code vagaviva.security.login} e {@code vagaviva.security.bootstrap-admin}. */
@Validated
@ConfigurationProperties("vagaviva.security")
public record IdentityProperties(@Valid @NotNull Login login, @Valid @NotNull BootstrapAdmin bootstrapAdmin) {

    public record Login(@Min(1) int maxAttempts, @NotNull Duration lockDuration) {

        public LockoutPolicy toPolicy() {
            return new LockoutPolicy(maxAttempts, lockDuration);
        }
    }

    /** Senha vinda do segredo {@code BOOTSTRAP_ADMIN_PASSWORD}; vazia ⇒ não cria o admin. */
    public record BootstrapAdmin(@NotBlank @Email String email, @Nullable String password) {
    }
}
