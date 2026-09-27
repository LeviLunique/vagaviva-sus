package br.com.vagaviva.regulation.adapter.out.persistence;

import br.com.vagaviva.regulation.application.port.out.ProtocolSequence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ProtocolSequenceAdapter implements ProtocolSequence {

    private final JdbcTemplate jdbc;

    ProtocolSequenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long next() {
        return jdbc.queryForObject("SELECT nextval('referral_protocol_seq')", Long.class);
    }
}
