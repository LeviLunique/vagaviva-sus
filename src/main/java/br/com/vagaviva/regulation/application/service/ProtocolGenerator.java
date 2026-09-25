package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.regulation.application.port.out.ProtocolSequence;
import br.com.vagaviva.regulation.domain.Protocol;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** RN-04: ano da data civil (fuso de negócio) + sequencial do banco. */
@Component
class ProtocolGenerator {

    private final ProtocolSequence sequence;
    private final Clock clock;

    ProtocolGenerator(ProtocolSequence sequence, Clock clock) {
        this.sequence = sequence;
        this.clock = clock;
    }

    Protocol next() {
        return Protocol.of(LocalDate.now(clock).getYear(), sequence.next());
    }
}
