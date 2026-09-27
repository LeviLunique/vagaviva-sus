package br.com.vagaviva.scheduling.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * RN-11: o paciente confirma até D-3 às 23h59 no fuso de negócio — o prazo é uma data civil, não
 * "72 horas antes": uma consulta às 08h de sexta tem prazo até 23h59 de terça.
 */
public final class ConfirmationPolicy {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    private final int daysBeforeStart;
    private final ZoneId zone;

    public ConfirmationPolicy(int daysBeforeStart, ZoneId zone) {
        if (daysBeforeStart < 1) {
            throw new IllegalArgumentException("O prazo deve ser de pelo menos 1 dia antes do atendimento");
        }
        this.daysBeforeStart = daysBeforeStart;
        this.zone = zone;
    }

    public Instant deadlineFor(Instant startAt) {
        LocalDate startDate = startAt.atZone(zone).toLocalDate();
        return startDate.minusDays(daysBeforeStart).atTime(END_OF_DAY).atZone(zone).toInstant();
    }
}
