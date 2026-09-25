package br.com.vagaviva.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Gerador de identificadores UUID versão 7 (RFC 9562): 48 bits de timestamp Unix em ms,
 * 12 bits de contador monotônico e 62 bits aleatórios. Ids ordenáveis no tempo mantêm os
 * índices B-tree do PostgreSQL compactos e não expõem sequência previsível nas URLs.
 */
public final class Ids {

    private static final Generator GENERATOR = new Generator(System::currentTimeMillis);

    private Ids() {
    }

    public static UUID newId() {
        return GENERATOR.next();
    }

    /** Gerador com relógio injetável (testes); contador reinicia aleatoriamente a cada milissegundo. */
    static final class Generator {

        private static final int COUNTER_MAX = 0xFFF;
        private static final int COUNTER_SEED_BOUND = 0x800;

        private final SecureRandom random = new SecureRandom();
        private final LongSupplier clock;
        private long lastMillis = -1;
        private int counter;

        Generator(LongSupplier clock) {
            this.clock = clock;
        }

        synchronized UUID next() {
            long now = clock.getAsLong();
            if (now > lastMillis) {
                lastMillis = now;
                counter = random.nextInt(COUNTER_SEED_BOUND);
            } else if (++counter > COUNTER_MAX) {
                // Contador esgotado (ou relógio voltou): avança o timestamp lógico para manter a ordem.
                lastMillis++;
                counter = 0;
            }
            long msb = (lastMillis << 16) | 0x7000L | counter;
            long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
            return new UUID(msb, lsb);
        }
    }
}
