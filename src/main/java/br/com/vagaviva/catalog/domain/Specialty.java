package br.com.vagaviva.catalog.domain;

import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Especialidade ou exame (RF-07). {@code sensitive} (ex.: psiquiatria, infectologia) impede que o
 * nome apareça nas mensagens ao paciente (RN-20).
 */
public record Specialty(UUID id, String code, String name, SpecialtyType type, boolean sensitive, boolean active,
        Instant createdAt) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{2,20}");

    public Specialty {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessRuleException("INVALID_SPECIALTY_CODE",
                    "Código da especialidade inválido: use de 2 a 20 letras maiúsculas, números, '-' ou '_'.");
        }
    }

    public static Specialty register(String code, String name, SpecialtyType type, boolean sensitive, Clock clock) {
        return new Specialty(Ids.newId(), code.strip().toUpperCase(Locale.ROOT), name.strip(), type, sensitive, true,
                clock.instant());
    }
}
