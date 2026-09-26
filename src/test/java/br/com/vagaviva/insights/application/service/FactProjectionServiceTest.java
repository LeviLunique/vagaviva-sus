package br.com.vagaviva.insights.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.insights.application.port.out.FactStore;
import br.com.vagaviva.reallocation.events.SlotOfferAccepted;
import br.com.vagaviva.scheduling.AppointmentOrigin;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentConfirmed;
import br.com.vagaviva.scheduling.events.AppointmentExpired;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotLost;
import br.com.vagaviva.scheduling.events.SlotReleased;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID SPECIALTY = UUID.randomUUID();

    @Mock FactStore facts;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private FactProjectionService service;

    @BeforeEach
    void setUp() {
        service = new FactProjectionService(facts, meters, CLOCK);
    }

    private double count(String name, String... tags) {
        var counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private static AppointmentScheduled scheduled(AppointmentOrigin origin, Instant queueEnteredAt) {
        return new AppointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UNIT,
                SPECIALTY, NOW.plus(Duration.ofDays(10)), origin, NOW, queueEnteredAt);
    }

    @Test
    @DisplayName("agendamento vira fato com espera em dias (1 casa); encaixe já nasce confirmado; métrica só na 1ª vez")
    void shouldProjectScheduled() {
        AppointmentScheduled regular = scheduled(AppointmentOrigin.REGULAR, NOW.minus(Duration.ofHours(30 * 24 + 12)));
        when(facts.insertAppointment(eq(regular.appointmentId()), eq(SPECIALTY), eq(UNIT), eq("REGULAR"), eq(NOW),
                eq(regular.startAt()), eq(regular.queueEnteredAt()), eq(false), eq(new BigDecimal("40.5"))))
                .thenReturn(true, false);

        service.project(regular);
        service.project(regular);

        assertThat(count("vagaviva.appointments.scheduled", "origin", "REGULAR")).isEqualTo(1.0);
        verify(facts, never()).closeRelease(any(), any(), any());

        AppointmentScheduled offer = scheduled(AppointmentOrigin.SHORT_NOTICE_OFFER, null);
        when(facts.insertAppointment(eq(offer.appointmentId()), any(), any(), any(), any(), any(), isNull(), eq(true),
                isNull())).thenReturn(true);
        service.project(offer);
        assertThat(count("vagaviva.appointments.scheduled", "origin", "SHORT_NOTICE_OFFER")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("realocação pela fila fecha a liberação em aberto como REALLOCATED (via QUEUE)")
    void shouldCloseReleaseOnReallocation() {
        AppointmentScheduled reallocated = scheduled(AppointmentOrigin.REALLOCATED, NOW);
        when(facts.insertAppointment(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(true);
        when(facts.closeRelease(reallocated.slotId(), "REALLOCATED", NOW)).thenReturn(true);

        service.project(reallocated);

        assertThat(count("vagaviva.slots.reallocated", "via", "QUEUE")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("desfechos do agendamento: confirmado, atendido, falta, cancelado (por motivo) e expirado")
    void shouldProjectOutcomes() {
        UUID id = UUID.randomUUID();
        when(facts.markConfirmed(id)).thenReturn(true);
        when(facts.closeAppointment(eq(id), any(), eq(NOW))).thenReturn(true);

        service.project(new AppointmentConfirmed(id, UUID.randomUUID(), UNIT, SPECIALTY, NOW));
        service.project(new AppointmentAttended(id, UUID.randomUUID(), UNIT, SPECIALTY, NOW));
        service.project(new AppointmentMissed(id, UUID.randomUUID(), UNIT, SPECIALTY, NOW));
        for (AppointmentCancelled.Reason reason : AppointmentCancelled.Reason.values()) {
            service.project(new AppointmentCancelled(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW,
                    reason));
        }
        service.project(new AppointmentExpired(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW));

        verify(facts).closeAppointment(id, "ATTENDED", NOW);
        verify(facts).closeAppointment(id, "NO_SHOW", NOW);
        verify(facts).closeAppointment(id, "CANCELLED_BY_UNIT", NOW);
        verify(facts).closeAppointment(id, "CANCELLED_BY_PATIENT", NOW);
        verify(facts).closeAppointment(id, "WITHDRAWN", NOW);
        verify(facts).closeAppointment(id, "EXPIRED_UNCONFIRMED", NOW);
        for (String status : new String[] {"CONFIRMED", "ATTENDED", "NO_SHOW", "WITHDRAWN", "EXPIRED_UNCONFIRMED"}) {
            assertThat(count("vagaviva.appointments.outcome", "status", status)).as(status).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("desfecho repetido (evento reprocessado) não conta de novo")
    void shouldNotCountRepeatedOutcome() {
        UUID id = UUID.randomUUID();
        when(facts.closeAppointment(id, "ATTENDED", NOW)).thenReturn(false);

        service.project(new AppointmentAttended(id, UUID.randomUUID(), UNIT, SPECIALTY, NOW));

        assertThat(count("vagaviva.appointments.outcome", "status", "ATTENDED")).isZero();
    }

    @Test
    @DisplayName("liberação: id estável por (vaga, nº da liberação); em cima da hora já nasce LOST; aceite e perda fecham")
    void shouldProjectReleases() {
        UUID slot = UUID.randomUUID();
        UUID firstId = UUID.nameUUIDFromBytes((slot + ":1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(facts.insertRelease(eq(firstId), eq(slot), eq(SPECIALTY), eq(UNIT), eq("PATIENT_CANCELLED"), eq(NOW),
                isNull())).thenReturn(true);
        service.project(new SlotReleased(slot, UNIT, SPECIALTY, SlotReleased.Reason.PATIENT_CANCELLED,
                SlotStatus.OPEN_FOR_OFFERS, NOW.plus(Duration.ofDays(2)), 1, NOW));

        UUID late = UUID.randomUUID();
        service.project(new SlotReleased(late, UNIT, SPECIALTY, SlotReleased.Reason.CONFIRMATION_EXPIRED,
                SlotStatus.EXPIRED, NOW.plus(Duration.ofHours(1)), 1, NOW));
        verify(facts).insertRelease(any(), eq(late), any(), any(), eq("CONFIRMATION_EXPIRED"), eq(NOW), eq("LOST"));

        when(facts.closeRelease(slot, "OFFER_ACCEPTED", NOW)).thenReturn(true);
        service.project(new SlotOfferAccepted(UUID.randomUUID(), slot, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, NOW));
        service.project(new SlotLost(late, UNIT, SPECIALTY, NOW));

        assertThat(count("vagaviva.slots.released", "reason", "PATIENT_CANCELLED")).isEqualTo(1.0);
        assertThat(count("vagaviva.slots.reallocated", "via", "OFFER")).isEqualTo(1.0);
        assertThat(count("vagaviva.slots.lost")).isEqualTo(1.0);
        verify(facts).closeRelease(late, "LOST", NOW);
    }
}
