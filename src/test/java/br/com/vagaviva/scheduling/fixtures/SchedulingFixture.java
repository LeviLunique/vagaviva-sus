package br.com.vagaviva.scheduling.fixtures;

import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.ConfirmationPolicy;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.domain.SlotLeadTimes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Vagas e agendamentos fictícios (Test Data Builder). */
public final class SchedulingFixture {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    /** Sexta-feira, 25/09/2026 09:00 em São Paulo. */
    public static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, SAO_PAULO);
    public static final SlotLeadTimes LEAD_TIMES = new SlotLeadTimes(Duration.ofDays(5), Duration.ofHours(2));
    public static final ConfirmationPolicy CONFIRMATION = new ConfirmationPolicy(3, SAO_PAULO);
    public static final UUID UNIT = UUID.fromString("0199c0de-0000-7000-8000-000000000201");
    public static final UUID SPECIALTY = UUID.fromString("0199c0de-0000-7000-8000-000000000301");

    private SchedulingFixture() {
    }

    /** Vaga daqui a 10 dias às 08:00 (São Paulo) — alocação regular. */
    public static Slot anAvailableSlot() {
        return Slot.publish(UNIT, SPECIALTY, "Dra. Ana Fictícia", Instant.parse("2026-10-05T11:00:00Z"), 30, LEAD_TIMES,
                CLOCK);
    }

    public static Appointment aPendingAppointment() {
        Slot slot = anAvailableSlot();
        slot.allocate(CLOCK);
        return Appointment.schedule(slot, UUID.randomUUID(), UUID.randomUUID(), CONFIRMATION, CLOCK);
    }

    public static Clock at(Instant instant) {
        return Clock.fixed(instant, SAO_PAULO);
    }
}
