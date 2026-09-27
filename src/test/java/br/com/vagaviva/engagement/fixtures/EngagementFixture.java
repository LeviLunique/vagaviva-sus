package br.com.vagaviva.engagement.fixtures;

import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.engagement.application.service.EngagementProperties;
import br.com.vagaviva.engagement.application.service.EngagementProperties.ChannelMode;
import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.AppointmentView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Dados fictícios do engajamento. */
public final class EngagementFixture {

    public static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    public static final UUID UNIT = UUID.fromString("0199c0de-0000-7000-8000-000000000201");
    public static final UUID SPECIALTY = UUID.fromString("0199c0de-0000-7000-8000-000000000301");

    private EngagementFixture() {
    }

    public static EngagementProperties properties(ChannelMode mode, boolean sms, boolean whatsapp) {
        return new EngagementProperties(mode, new EngagementProperties.Sms(sms),
                new EngagementProperties.WhatsApp(whatsapp, "phone-number-id-1", "vagaviva_aviso"),
                "vagaviva-test-notifications", Duration.ofHours(24), Duration.ofHours(24));
    }

    public static PatientSummary patient(UUID id, boolean whatsappOptIn, boolean active) {
        return new PatientSummary(id, "Maria", "***********1234", LocalDate.of(1950, 1, 1), "3550308",
                "+5511999990001", ContactChannel.WHATSAPP, whatsappOptIn, true, active);
    }

    /** Agendamento em 10 dias, aguardando confirmação. */
    public static AppointmentView appointment(AppointmentStatus status) {
        Instant start = NOW.plus(Duration.ofDays(10));
        return new AppointmentView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UNIT,
                SPECIALTY, start, AppointmentOrigin.REGULAR, status, start.minus(Duration.ofDays(3)));
    }

    public static HealthUnitSummary unit() {
        return new HealthUnitSummary(UNIT, "9900201", "AME Zona Norte", HealthUnitType.SPECIALIZED, "3550308",
                "São Paulo", "Av. Norte, 1500", Set.of(), true);
    }

    public static SpecialtySummary specialty(SpecialtyType type, boolean sensitive) {
        return new SpecialtySummary(SPECIALTY, "PSIQ", "Psiquiatria", type, sensitive, true);
    }
}
