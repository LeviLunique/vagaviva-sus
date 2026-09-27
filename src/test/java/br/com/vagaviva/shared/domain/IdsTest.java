package br.com.vagaviva.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    @DisplayName("gera UUID versão 7 com a variante da RFC 9562")
    void shouldGenerateVersion7Uuid() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("os 48 bits mais altos carregam o instante de criação em milissegundos")
    void shouldEmbedCreationTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = Ids.newId();
        long after = System.currentTimeMillis();

        long embedded = id.getMostSignificantBits() >>> 16;
        assertThat(embedded).isBetween(before, after + 1);
    }

    @Test
    @DisplayName("ids gerados em sequência são únicos e crescentes (ordenáveis no tempo)")
    void shouldBeUniqueAndMonotonic() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            ids.add(Ids.newId());
        }

        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        assertThat(ids).isSortedAccordingTo(UUID::compareTo);
    }

    @Test
    @DisplayName("com o relógio parado, o contador mantém a ordem mesmo ao estourar no mesmo milissegundo")
    void shouldKeepOrderWhenCounterOverflowsWithinSameMillisecond() {
        var generator = new Ids.Generator(() -> 1_700_000_000_000L);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.next());
        }

        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        assertThat(ids).isSortedAccordingTo(UUID::compareTo);
        assertThat(ids.getFirst().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("se o relógio voltar no tempo, os ids continuam crescentes")
    void shouldStayMonotonicWhenClockGoesBackwards() {
        long[] now = {1_700_000_000_500L};
        var generator = new Ids.Generator(() -> now[0]);

        UUID first = generator.next();
        now[0] -= 400;
        UUID second = generator.next();

        assertThat(second).isGreaterThan(first);
    }
}
