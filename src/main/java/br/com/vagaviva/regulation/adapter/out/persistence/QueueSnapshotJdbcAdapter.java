package br.com.vagaviva.regulation.adapter.out.persistence;

import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Snapshot da fila em SQL puro (janela {@code ROW_NUMBER}), dentro da transação de quem chama. */
@Component
class QueueSnapshotJdbcAdapter implements QueueSnapshotRepository {

    private static final String REBUILD_POSITIONS = """
            INSERT INTO queue_position (referral_id, specialty_id, position, total_in_queue, risk_class, snapshot_at)
            SELECT id, specialty_id,
                   ROW_NUMBER() OVER (PARTITION BY specialty_id
                                      ORDER BY risk_rank, priority_group DESC, queue_entered_at, id),
                   COUNT(*) OVER (PARTITION BY specialty_id),
                   risk_class, :snapshotAt
              FROM referral
             WHERE status = 'WAITING'""";

    private static final String REBUILD_STATS = """
            INSERT INTO queue_specialty_stats (specialty_id, waiting_red, waiting_yellow, waiting_green, waiting_blue,
                                               avg_wait_days, throughput_per_day, snapshot_at)
            SELECT specialty_id,
                   COUNT(*) FILTER (WHERE status = 'WAITING' AND risk_class = 'RED'),
                   COUNT(*) FILTER (WHERE status = 'WAITING' AND risk_class = 'YELLOW'),
                   COUNT(*) FILTER (WHERE status = 'WAITING' AND risk_class = 'GREEN'),
                   COUNT(*) FILTER (WHERE status = 'WAITING' AND risk_class = 'BLUE'),
                   ROUND((AVG(EXTRACT(EPOCH FROM (scheduled_at - queue_entered_at)) / 86400)
                          FILTER (WHERE scheduled_at >= :since))::numeric, 1),
                   ROUND(NULLIF(COUNT(*) FILTER (WHERE scheduled_at >= :since), 0)::numeric / :windowDays, 2),
                   :snapshotAt
              FROM referral
             WHERE status = 'WAITING' OR scheduled_at >= :since
             GROUP BY specialty_id""";

    private final NamedParameterJdbcTemplate jdbc;

    QueueSnapshotJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int rebuild(Instant snapshotAt, Instant throughputSince, int throughputWindowDays) {
        var params = new MapSqlParameterSource("snapshotAt", Timestamp.from(snapshotAt))
                .addValue("since", Timestamp.from(throughputSince))
                .addValue("windowDays", throughputWindowDays);
        jdbc.getJdbcTemplate().update("DELETE FROM queue_position");
        int queued = jdbc.update(REBUILD_POSITIONS, params);
        jdbc.getJdbcTemplate().update("DELETE FROM queue_specialty_stats");
        jdbc.update(REBUILD_STATS, params);
        return queued;
    }

    @Override
    public Optional<QueuePositionSnapshot> findPosition(UUID referralId) {
        return jdbc.query("SELECT * FROM queue_position WHERE referral_id = :id",
                new MapSqlParameterSource("id", referralId), (rs, row) -> new QueuePositionSnapshot(
                        rs.getObject("referral_id", UUID.class), rs.getObject("specialty_id", UUID.class),
                        rs.getInt("position"), rs.getInt("total_in_queue"), RiskClass.valueOf(rs.getString("risk_class")),
                        rs.getTimestamp("snapshot_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public Optional<SpecialtyQueueStats> findStats(UUID specialtyId) {
        return jdbc.query("SELECT * FROM queue_specialty_stats WHERE specialty_id = :id",
                new MapSqlParameterSource("id", specialtyId), (rs, row) -> stats(rs)).stream().findFirst();
    }

    @Override
    public List<SpecialtyQueueStats> findAllStats() {
        return jdbc.query("SELECT * FROM queue_specialty_stats", (rs, row) -> stats(rs));
    }

    private static SpecialtyQueueStats stats(ResultSet rs) throws SQLException {
        return new SpecialtyQueueStats(rs.getObject("specialty_id", UUID.class), rs.getInt("waiting_red"),
                rs.getInt("waiting_yellow"), rs.getInt("waiting_green"), rs.getInt("waiting_blue"),
                rs.getBigDecimal("avg_wait_days"), rs.getBigDecimal("throughput_per_day"),
                rs.getTimestamp("snapshot_at").toInstant());
    }
}
