package br.com.vagaviva.insights.adapter.out.persistence;

import br.com.vagaviva.insights.application.port.out.FactStore;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Fatos gravados com {@code ON CONFLICT DO NOTHING} e atualizações condicionais: reprocessar não muda nada. */
@Component
class JdbcFactStore implements FactStore {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcFactStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertAppointment(UUID appointmentId, UUID specialtyId, UUID unitId, String origin,
            Instant scheduledAt, Instant startAt, @Nullable Instant queueEnteredAt, boolean confirmed,
            @Nullable BigDecimal waitingDays) {
        return jdbc.update("""
                INSERT INTO appointment_fact (appointment_id, specialty_id, unit_id, origin, scheduled_at, start_at,
                                              queue_entered_at, confirmed, waiting_days)
                VALUES (:id, :specialty, :unit, :origin, :scheduledAt, :startAt, :queueEnteredAt, :confirmed, :waitingDays)
                ON CONFLICT (appointment_id) DO NOTHING""",
                new MapSqlParameterSource("id", appointmentId).addValue("specialty", specialtyId).addValue("unit", unitId)
                        .addValue("origin", origin).addValue("scheduledAt", ts(scheduledAt)).addValue("startAt", ts(startAt))
                        .addValue("queueEnteredAt", ts(queueEnteredAt)).addValue("confirmed", confirmed)
                        .addValue("waitingDays", waitingDays)) == 1;
    }

    @Override
    public boolean markConfirmed(UUID appointmentId) {
        return jdbc.update("UPDATE appointment_fact SET confirmed = true WHERE appointment_id = :id AND NOT confirmed",
                new MapSqlParameterSource("id", appointmentId)) == 1;
    }

    @Override
    public boolean closeAppointment(UUID appointmentId, String finalStatus, Instant outcomeAt) {
        return jdbc.update("""
                UPDATE appointment_fact SET final_status = :status, outcome_at = :at
                WHERE appointment_id = :id AND final_status IS NULL""",
                new MapSqlParameterSource("id", appointmentId).addValue("status", finalStatus)
                        .addValue("at", ts(outcomeAt))) == 1;
    }

    @Override
    public boolean insertRelease(UUID id, UUID slotId, UUID specialtyId, UUID unitId, String reason,
            Instant releasedAt, @Nullable String outcome) {
        return jdbc.update("""
                INSERT INTO slot_release_fact (id, slot_id, specialty_id, unit_id, reason, released_at, outcome, outcome_at)
                VALUES (:id, :slot, :specialty, :unit, :reason, :releasedAt, :outcome,
                        CASE WHEN CAST(:outcome AS varchar) IS NULL THEN NULL ELSE CAST(:releasedAt AS timestamptz) END)
                ON CONFLICT (id) DO NOTHING""",
                new MapSqlParameterSource("id", id).addValue("slot", slotId).addValue("specialty", specialtyId)
                        .addValue("unit", unitId).addValue("reason", reason).addValue("releasedAt", ts(releasedAt))
                        .addValue("outcome", outcome)) == 1;
    }

    @Override
    public boolean closeRelease(UUID slotId, String outcome, Instant outcomeAt) {
        return jdbc.update("""
                UPDATE slot_release_fact SET outcome = :outcome, outcome_at = :at
                WHERE slot_id = :slot AND outcome IS NULL""",
                new MapSqlParameterSource("slot", slotId).addValue("outcome", outcome).addValue("at", ts(outcomeAt))) > 0;
    }

    private static @Nullable Timestamp ts(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
