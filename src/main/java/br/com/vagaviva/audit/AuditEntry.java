package br.com.vagaviva.audit;

import br.com.vagaviva.shared.security.Role;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Registro a auditar. {@code action} em {@code UPPER_SNAKE_CASE} (ex.: {@code LOGIN_FAILED});
 * {@code details} só com identificadores e códigos — nunca nome, CNS, CPF, telefone ou senha.
 */
public record AuditEntry(
        @Nullable UUID actorId,
        @Nullable Role actorRole,
        String action,
        String resourceType,
        @Nullable String resourceId,
        AuditOutcome outcome,
        @Nullable String clientIp,
        Map<String, String> details) {

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(outcome, "outcome");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
