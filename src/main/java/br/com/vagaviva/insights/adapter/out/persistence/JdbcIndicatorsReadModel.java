package br.com.vagaviva.insights.adapter.out.persistence;

import br.com.vagaviva.insights.application.port.out.IndicatorsReadModel;
import br.com.vagaviva.insights.domain.PeriodCounts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Agendamentos pelo início do atendimento ({@code start_at}); liberações pelo momento da liberação
 * ({@code released_at}). Filtros de especialidade e unidade opcionais.
 */
@Component
class JdbcIndicatorsReadModel implements IndicatorsReadModel {

    private static final String APPOINTMENTS = """
            SELECT %s count(*) AS scheduled,
                   count(*) FILTER (WHERE confirmed AND origin <> 'SHORT_NOTICE_OFFER') AS confirmed,
                   count(*) FILTER (WHERE final_status = 'EXPIRED_UNCONFIRMED') AS expired_unconfirmed,
                   count(*) FILTER (WHERE final_status IN ('CANCELLED_BY_UNIT','CANCELLED_BY_PATIENT','WITHDRAWN')) AS cancelled,
                   count(*) FILTER (WHERE final_status = 'ATTENDED') AS attended,
                   count(*) FILTER (WHERE final_status = 'NO_SHOW') AS no_shows,
                   avg(waiting_days) FILTER (WHERE final_status = 'ATTENDED') AS avg_waiting_days
            FROM appointment_fact
            WHERE start_at >= :from AND start_at < :to %s""";

    private static final String RELEASES = """
            SELECT %s count(*) AS released,
                   count(*) FILTER (WHERE outcome IN ('REALLOCATED','OFFER_ACCEPTED')) AS reallocated,
                   count(*) FILTER (WHERE outcome = 'LOST') AS lost,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM outcome_at - released_at) / 3600)
                       FILTER (WHERE outcome IN ('REALLOCATED','OFFER_ACCEPTED')) AS median_hours
            FROM slot_release_fact
            WHERE released_at >= :from AND released_at < :to %s""";

    private static final String FILTERS = """
            AND (CAST(:specialty AS uuid) IS NULL OR specialty_id = :specialty)
            AND (CAST(:unit AS uuid) IS NULL OR unit_id = :unit)""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcIndicatorsReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PeriodCounts counts(Instant from, Instant to, @Nullable UUID specialtyId, @Nullable UUID unitId) {
        MapSqlParameterSource params = period(from, to).addValue("specialty", specialtyId).addValue("unit", unitId);
        PeriodCounts appointments = jdbc.queryForObject(APPOINTMENTS.formatted("", FILTERS), params,
                (rs, n) -> appointments(rs));
        return jdbc.queryForObject(RELEASES.formatted("", FILTERS), params, (rs, n) -> withReleases(appointments, rs));
    }

    @Override
    public Map<UUID, PeriodCounts> countsBySpecialty(Instant from, Instant to) {
        Map<UUID, PeriodCounts> bySpecialty = new HashMap<>();
        MapSqlParameterSource params = period(from, to);
        jdbc.query(APPOINTMENTS.formatted("specialty_id,", "GROUP BY specialty_id"), params,
                rs -> {
                    bySpecialty.put(rs.getObject("specialty_id", UUID.class), appointments(rs));
                });
        jdbc.query(RELEASES.formatted("specialty_id,", "GROUP BY specialty_id"), params,
                rs -> {
                    UUID specialty = rs.getObject("specialty_id", UUID.class);
                    bySpecialty.put(specialty, withReleases(bySpecialty.getOrDefault(specialty, PeriodCounts.empty()), rs));
                });
        return bySpecialty;
    }

    private static MapSqlParameterSource period(Instant from, Instant to) {
        return new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
    }

    private static PeriodCounts appointments(ResultSet rs) throws SQLException {
        return new PeriodCounts(rs.getLong("scheduled"), rs.getLong("confirmed"), rs.getLong("expired_unconfirmed"),
                rs.getLong("cancelled"), rs.getLong("attended"), rs.getLong("no_shows"),
                nullableDouble(rs, "avg_waiting_days"), 0, 0, 0, null);
    }

    private static PeriodCounts withReleases(PeriodCounts c, ResultSet rs) throws SQLException {
        return new PeriodCounts(c.scheduled(), c.confirmed(), c.expiredUnconfirmed(), c.cancelled(), c.attended(),
                c.noShows(), c.avgWaitingDays(), rs.getLong("released"), rs.getLong("reallocated"), rs.getLong("lost"),
                nullableDouble(rs, "median_hours"));
    }

    private static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
