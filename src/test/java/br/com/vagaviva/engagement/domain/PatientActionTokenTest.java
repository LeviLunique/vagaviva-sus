package br.com.vagaviva.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientActionTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("RF-25: 128 bits em Base64URL (22 caracteres), guardado só como SHA-256 hexadecimal")
    void shouldIssueOpaqueTokenStoredAsHash() {
        var issued = PatientActionToken.issue(TokenPurpose.APPOINTMENT, UUID.randomUUID(), NOW.plusSeconds(3600), CLOCK);

        assertThat(issued.raw()).hasSize(22).matches("[A-Za-z0-9_-]{22}");
        assertThat(issued.token().tokenHash()).hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(PatientActionToken.hash(issued.raw())).doesNotContain(issued.raw());
    }

    @Test
    @DisplayName("entropia: 10 mil tokens sem repetição")
    void shouldNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tokens.add(PatientActionToken.issue(TokenPurpose.APPOINTMENT, UUID.randomUUID(), NOW, CLOCK).raw());
        }
        assertThat(tokens).hasSize(10_000);
    }

    @Test
    @DisplayName("expira no início do atendimento (inclusive); registra o último uso")
    void shouldExpireAtDeadline() {
        var token = PatientActionToken.issue(TokenPurpose.APPOINTMENT, UUID.randomUUID(), NOW.plusSeconds(60), CLOCK).token();

        assertThat(token.isExpired(CLOCK)).isFalse();
        assertThat(token.isExpired(Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC))).isTrue();
        token.markUsed(CLOCK);
        assertThat(token.lastUsedAt()).isEqualTo(NOW);
    }
}
