package br.com.vagaviva.identity.domain;

import br.com.vagaviva.shared.domain.BusinessRuleException;
import java.nio.charset.StandardCharsets;

/**
 * RN-02: senha com no mínimo 10 caracteres, contendo letras e números. O teto de 72 bytes é o
 * limite do BCrypt — acima disso o restante seria ignorado silenciosamente.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH
                || rawPassword.chars().noneMatch(Character::isLetter)
                || rawPassword.chars().noneMatch(Character::isDigit)) {
            throw new BusinessRuleException("WEAK_PASSWORD",
                    "A senha deve ter no mínimo %d caracteres, com letras e números.".formatted(MIN_LENGTH));
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessRuleException("PASSWORD_TOO_LONG",
                    "A senha deve ter no máximo %d bytes.".formatted(MAX_BYTES));
        }
    }
}
