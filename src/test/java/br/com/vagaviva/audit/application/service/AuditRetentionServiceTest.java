package br.com.vagaviva.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.vagaviva.audit.application.port.out.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditRetentionServiceTest {

    @Test
    @DisplayName("SPEC §10: apaga só o que passou de 5 anos (calendário, com ano bissexto)")
    void shouldPurgeOlderThanRetention() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2031-09-26T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        when(repository.deleteOlderThan(Instant.parse("2026-09-26T12:00:00Z"))).thenReturn(3, 0);
        var service = new AuditRetentionService(repository, Period.ofYears(5), clock);

        assertThat(service.purge()).isEqualTo(3);
        assertThat(service.purge()).isZero();
    }
}
