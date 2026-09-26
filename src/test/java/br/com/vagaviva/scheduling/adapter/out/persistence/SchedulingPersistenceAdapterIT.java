package br.com.vagaviva.scheduling.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotFilter;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.ConfirmationPolicy;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.domain.SlotLeadTimes;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SchedulingPersistenceAdapterIT {

    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired SlotLeadTimes leadTimes;
    @Autowired ConfirmationPolicy confirmationPolicy;
    @Autowired TransactionTemplate transactions;
    @Autowired Clock clock;

    private Instant base() {
        return clock.instant().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.HOURS);
    }

    private Slot slot(UUID unit, String professional, Instant start) {
        return Slot.publish(unit, UUID.randomUUID(), professional, start, 30, leadTimes, clock);
    }

    @Test
    @DisplayName("RF-19 CA1 no banco: sobreposição parcial do mesmo profissional (sem diferenciar maiúsculas); cancelada não conta")
    void shouldDetectOverlapsInDatabase() {
        UUID unit = UUID.randomUUID();
        Slot saved = slots.saveAll(List.of(slot(unit, "Dra. Sobreposição", base()))).getFirst();

        assertThat(slots.existsOverlap(unit, "dra. sobreposição", base().plusSeconds(900), base().plusSeconds(2700))).isTrue();
        assertThat(slots.existsOverlap(unit, "Dra. Sobreposição", saved.endAt(), saved.endAt().plusSeconds(1800))).isFalse();
        assertThat(slots.existsOverlap(UUID.randomUUID(), "Dra. Sobreposição", base(), base().plusSeconds(60))).isFalse();

        saved.cancel(clock);
        slots.save(saved);
        assertThat(slots.existsOverlap(unit, "Dra. Sobreposição", base(), base().plusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("índice único (unidade, profissional, início) vira SLOT_OVERLAP")
    void shouldTranslateUniqueIndex() {
        UUID unit = UUID.randomUUID();
        slots.saveAll(List.of(slot(unit, "Dr. Único", base())));

        assertThatThrownBy(() -> slots.save(slot(unit, "Dr. Único", base())))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("SLOT_OVERLAP");
    }

    @Test
    @DisplayName("filtros da agenda: unidade, especialidade, status e período; alocáveis por horário")
    void shouldSearchAndListAllocatable() {
        UUID unit = UUID.randomUUID();
        Slot early = slot(unit, "Dra. Filtro", base());
        Slot late = slot(unit, "Dra. Filtro", base().plus(Duration.ofDays(1)));
        slots.saveAll(List.of(late, early));

        var byPeriod = slots.search(new SlotFilter(unit, null, base(), base().plus(Duration.ofHours(1)),
                SlotStatus.AVAILABLE), PageRequest.of(0, 10));
        var bySpecialty = slots.search(new SlotFilter(null, late.specialtyId(), null, null, null), PageRequest.of(0, 10));

        assertThat(byPeriod.getContent()).extracting(Slot::id).containsExactly(early.id());
        assertThat(bySpecialty.getContent()).extracting(Slot::id).containsExactly(late.id());
        List<UUID> allocatable = slots.findAllocatableIds(base(), 1000);
        assertThat(allocatable.indexOf(early.id())).isLessThan(allocatable.indexOf(late.id()));
        assertThat(slots.findAllocatableIds(base().plus(Duration.ofHours(1)), 1000)).doesNotContain(early.id());
    }

    @Test
    @DisplayName("lockAvailable: vaga travada por outra transação é pulada (SKIP LOCKED); vaga já alocada também")
    void shouldSkipLockedAndAllocatedSlots() throws Exception {
        Slot free = slots.saveAll(List.of(slot(UUID.randomUUID(), "Dr. Trava", base()))).getFirst();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Boolean> holder = CompletableFuture.supplyAsync(() -> transactions.execute(status -> {
            boolean got = slots.lockAvailable(free.id()).isPresent();
            locked.countDown();
            await(release);
            return got;
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        Optional<Slot> second = transactions.execute(status -> slots.lockAvailable(free.id()));
        release.countDown();

        assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(second).isEmpty();

        Slot allocated = slots.findById(free.id()).orElseThrow();
        allocated.allocate(clock);
        slots.save(allocated);
        Optional<Slot> afterAllocation = transactions.execute(status -> slots.lockAvailable(free.id()));
        assertThat(afterAllocation).isEmpty();
    }

    @Test
    @DisplayName("agendamentos por unidade, data civil (São Paulo) e status; agendamento aberto da vaga")
    void shouldSearchAppointments() {
        UUID unit = UUID.randomUUID();
        Slot s = slots.saveAll(List.of(slot(unit, "Dra. Agenda", base()))).getFirst();
        s.allocate(clock);
        Slot allocated = slots.save(s);
        Appointment appointment = appointments.save(Appointment.schedule(allocated, UUID.randomUUID(), UUID.randomUUID(),
                confirmationPolicy, clock));
        LocalDate day = base().atZone(clock.getZone()).toLocalDate();

        assertThat(appointments.search(new AppointmentFilter(unit, day, AppointmentStatus.PENDING_CONFIRMATION),
                PageRequest.of(0, 10)).getContent()).extracting(Appointment::id).containsExactly(appointment.id());
        assertThat(appointments.search(new AppointmentFilter(unit, day.plusDays(1), null), PageRequest.of(0, 10))
                .getTotalElements()).isZero();
        assertThat(appointments.findOpenBySlot(allocated.id())).map(Appointment::id).contains(appointment.id());
        assertThat(appointments.findById(appointment.id())).isPresent();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
