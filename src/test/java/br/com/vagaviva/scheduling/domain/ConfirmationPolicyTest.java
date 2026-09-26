package br.com.vagaviva.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfirmationPolicyTest {

    private final ConfirmationPolicy policy = new ConfirmationPolicy(3, ZoneId.of("America/Sao_Paulo"));

    @ParameterizedTest(name = "início {0} ⇒ prazo {1}")
    @CsvSource({
        // 05/10 08:00 SP ⇒ 02/10 23:59 SP
        "2026-10-05T11:00:00Z, 2026-10-03T02:59:00Z",
        // 05/10 00:30 SP (ainda 05/10 na data civil, 03:30Z) ⇒ 02/10 23:59 SP
        "2026-10-05T03:30:00Z, 2026-10-03T02:59:00Z",
        // 05/10 01:30Z é 04/10 22:30 em SP ⇒ prazo conta a partir de 04/10 ⇒ 01/10 23:59 SP
        "2026-10-05T01:30:00Z, 2026-10-02T02:59:00Z",
        // virada de mês/ano: 02/01/2027 10:00 SP ⇒ 30/12/2026 23:59 SP
        "2027-01-02T13:00:00Z, 2026-12-31T02:59:00Z"
    })
    @DisplayName("RN-11: D-3 às 23h59 pela data civil de São Paulo, não 72 horas corridas")
    void shouldComputeDeadlineByCivilDate(Instant start, Instant expected) {
        assertThat(policy.deadlineFor(start)).isEqualTo(expected);
    }

    @Test
    @DisplayName("sem horário de verão (abolido em 2019): em novembro o fuso segue UTC-3")
    void shouldIgnoreAbolishedDaylightSaving() {
        // 20/11/2026 09:00 SP ⇒ 17/11/2026 23:59 SP = 18/11 02:59Z (seria 01:59Z com o antigo horário de verão)
        assertThat(policy.deadlineFor(Instant.parse("2026-11-20T12:00:00Z"))).isEqualTo(Instant.parse("2026-11-18T02:59:00Z"));
    }

    @Test
    @DisplayName("prazo precisa ser de ao menos 1 dia antes")
    void shouldValidateDays() {
        assertThatThrownBy(() -> new ConfirmationPolicy(0, ZoneId.of("UTC"))).isInstanceOf(IllegalArgumentException.class);
    }
}
