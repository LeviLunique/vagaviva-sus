package br.com.vagaviva.scheduling.application.service;

import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CLOCK;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.CONFIRMATION;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.LEAD_TIMES;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.NOW;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.SPECIALTY;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.UNIT;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.aPendingAppointment;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.anAvailableSlot;
import static br.com.vagaviva.scheduling.fixtures.SchedulingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.HealthUnitSummary;
import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.QueueApi;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.ReturnReason;
import br.com.vagaviva.regulation.ReviewReason;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.scheduling.AppointmentStatus;
import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.application.port.in.AppointmentUseCases.AppointmentFilter;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.PublishSlotsCommand;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotFilter;
import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotTime;
import br.com.vagaviva.scheduling.application.port.out.AppointmentRepository;
import br.com.vagaviva.scheduling.application.port.out.SlotRepository;
import br.com.vagaviva.scheduling.domain.Appointment;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.scheduling.events.AppointmentAttended;
import br.com.vagaviva.scheduling.events.AppointmentCancelled;
import br.com.vagaviva.scheduling.events.AppointmentMissed;
import br.com.vagaviva.scheduling.events.AppointmentScheduled;
import br.com.vagaviva.scheduling.events.SlotOpenedForOffers;
import br.com.vagaviva.scheduling.events.SlotsPublished;
import br.com.vagaviva.shared.domain.BusinessRuleException;
import br.com.vagaviva.shared.domain.ForbiddenOperationException;
import br.com.vagaviva.shared.domain.NotFoundException;
import br.com.vagaviva.shared.security.CurrentUser;
import br.com.vagaviva.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class SchedulingServicesTest {

    private static final CurrentUser SCHEDULER = new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UNIT, "Sônia");
    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), Role.ADMIN, null, "Admin");
    private static final CurrentUser OTHER_SCHEDULER =
            new CurrentUser(UUID.randomUUID(), Role.SCHEDULER, UUID.randomUUID(), "Outra");
    private static final SchedulingProperties PROPERTIES = new SchedulingProperties(Duration.ofDays(5),
            Duration.ofHours(2), Duration.ofDays(3), Duration.ofMinutes(5), 200);

    @Mock SlotRepository slots;
    @Mock AppointmentRepository appointments;
    @Mock CatalogApi catalog;
    @Mock PatientApi patients;
    @Mock QueueApi queue;
    @Mock SchedulingAudit audit;
    @Mock ApplicationEventPublisher events;
    @Mock PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        lenient().when(slots.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(slots.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void unitIs(HealthUnitType type, boolean active, Set<String> area) {
        when(catalog.findUnit(UNIT)).thenReturn(Optional.of(new HealthUnitSummary(UNIT, "9900201", "AME", type,
                "3550308", "São Paulo", "Av. Norte", area, active)));
    }

    private void specialtyActive() {
        when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.of(new SpecialtySummary(SPECIALTY, "CARDIO",
                "Cardiologia", SpecialtyType.CONSULTATION, false, true)));
    }

    @Nested
    class Publish {

        private SlotService service() {
            return new SlotService(slots, appointments, catalog, queue, audit, events, LEAD_TIMES, CLOCK);
        }

        private PublishSlotsCommand command(CurrentUser actor, SlotTime... times) {
            return new PublishSlotsCommand(UNIT, SPECIALTY, "Dra. Ana", List.of(times), actor, "10.0.0.2");
        }

        @Test
        @DisplayName("RF-19: publica o lote, avisa do encaixe para vagas < 5 dias e dispara SlotsPublished")
        void shouldPublishBatch() {
            unitIs(HealthUnitType.SPECIALIZED, true, Set.of());
            specialtyActive();

            List<Slot> saved = service().publish(command(SCHEDULER,
                    new SlotTime(NOW.plus(Duration.ofDays(10)), 30), new SlotTime(NOW.plus(Duration.ofDays(3)), 30)));

            assertThat(saved).extracting(Slot::status).containsExactly(SlotStatus.OPEN_FOR_OFFERS, SlotStatus.AVAILABLE);
            verify(events).publishEvent(argThat((Object e) -> e instanceof SlotOpenedForOffers));
            verify(events).publishEvent(new SlotsPublished(UNIT, SPECIALTY, 2));
            verify(audit).record(SCHEDULER, SchedulingAudit.SLOTS_PUBLISHED, SchedulingAudit.SLOT, null, "10.0.0.2",
                    Map.of("unitId", UNIT.toString(), "count", "2"));
        }

        @Test
        @DisplayName("RN-03: SCHEDULER de outra unidade não publica (403)")
        void shouldRejectOtherUnit() {
            assertThatThrownBy(() -> service().publish(command(OTHER_SCHEDULER, new SlotTime(NOW.plus(Duration.ofDays(9)), 30))))
                    .isInstanceOf(ForbiddenOperationException.class).extracting("code").isEqualTo("SCHEDULING_OUT_OF_UNIT");
        }

        @Test
        @DisplayName("unidade UBS, inativa ou especialidade inativa ⇒ 422")
        void shouldValidateUnitAndSpecialty() {
            var time = new SlotTime(NOW.plus(Duration.ofDays(9)), 30);
            unitIs(HealthUnitType.PRIMARY_CARE, true, Set.of());
            assertThatThrownBy(() -> service().publish(command(ADMIN, time))).extracting("code").isEqualTo("INVALID_UNIT");

            unitIs(HealthUnitType.SPECIALIZED, false, Set.of());
            assertThatThrownBy(() -> service().publish(command(ADMIN, time))).extracting("code").isEqualTo("INVALID_UNIT");

            unitIs(HealthUnitType.SPECIALIZED, true, Set.of());
            when(catalog.findSpecialty(SPECIALTY)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service().publish(command(ADMIN, time))).extracting("code").isEqualTo("INVALID_SPECIALTY");
        }

        @Test
        @DisplayName("RF-19 CA1: sobreposição dentro do lote ou com vaga existente ⇒ SLOT_OVERLAP")
        void shouldRejectOverlaps() {
            unitIs(HealthUnitType.SPECIALIZED, true, Set.of());
            specialtyActive();
            Instant start = NOW.plus(Duration.ofDays(9));

            assertThatThrownBy(() -> service().publish(command(ADMIN, new SlotTime(start, 30),
                    new SlotTime(start.plus(Duration.ofMinutes(15)), 30))))
                    .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("SLOT_OVERLAP");

            when(slots.existsOverlap(eq(UNIT), eq("Dra. Ana"), any(), any())).thenReturn(true);
            assertThatThrownBy(() -> service().publish(command(ADMIN, new SlotTime(start, 30))))
                    .extracting("code").isEqualTo("SLOT_OVERLAP");
            verify(slots, never()).saveAll(any());
        }

        @Test
        @DisplayName("SCHEDULER lista só a própria unidade")
        void shouldScopeList() {
            when(slots.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

            service().list(new SlotFilter(UUID.randomUUID(), null, null, null, null), PageRequest.of(0, 20), SCHEDULER);

            verify(slots).search(new SlotFilter(UNIT, null, null, null, null), PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("RF-23: cancelar vaga com paciente ⇒ CANCELLED_BY_UNIT, volta à fila na mesma posição, evento UNIT")
        void shouldCancelSlotAndReturnPatientToQueue() {
            Appointment appointment = aPendingAppointment();
            Slot slot = anAvailableSlot();
            slot.allocate(CLOCK);
            when(slots.findById(slot.id())).thenReturn(Optional.of(slot));
            when(appointments.findOpenBySlot(slot.id())).thenReturn(Optional.of(appointment));

            Slot cancelled = service().cancel(slot.id(), " Profissional de férias ", SCHEDULER, null);

            assertThat(cancelled.status()).isEqualTo(SlotStatus.CANCELLED);
            assertThat(appointment.status()).isEqualTo(AppointmentStatus.CANCELLED_BY_UNIT);
            verify(queue).returnToQueue(appointment.referralId(), ReturnReason.UNIT_CANCELLED);
            verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentCancelled c
                    && c.reason() == AppointmentCancelled.Reason.UNIT));
            verify(audit).record(SCHEDULER, SchedulingAudit.SLOT_CANCELLED, SchedulingAudit.SLOT, slot.id(), null,
                    Map.of("reason", "Profissional de férias"));
        }

        @Test
        @DisplayName("cancelar vaga livre não mexe na fila; vaga de outra unidade ⇒ 403; inexistente ⇒ 404")
        void shouldCancelFreeSlotAndValidateAccess() {
            Slot slot = anAvailableSlot();
            when(slots.findById(slot.id())).thenReturn(Optional.of(slot));
            when(appointments.findOpenBySlot(slot.id())).thenReturn(Optional.empty());

            service().cancel(slot.id(), "Reforma", ADMIN, null);
            verify(queue, never()).returnToQueue(any(), any());

            Slot other = anAvailableSlot();
            when(slots.findById(other.id())).thenReturn(Optional.of(other));
            assertThatThrownBy(() -> service().cancel(other.id(), "x", OTHER_SCHEDULER, null))
                    .isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> service().cancel(UUID.randomUUID(), "x", ADMIN, null))
                    .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("SLOT_NOT_FOUND");
        }
    }

    @Nested
    class Allocation {

        private AllocationService service() {
            return new AllocationService(slots, appointments, queue, catalog, CONFIRMATION, PROPERTIES, audit, events,
                    transactionManager, CLOCK);
        }

        @Test
        @DisplayName("RF-20: aloca o próximo elegível da área da unidade, agenda com prazo e publica AppointmentScheduled")
        void shouldAllocateNextEligible() {
            Slot slot = anAvailableSlot();
            var candidate = new ReferralCandidate(UUID.randomUUID(), UUID.randomUUID(), SPECIALTY, RiskClass.RED, true,
                    NOW.minus(Duration.ofDays(30)), false);
            when(slots.findAllocatableIds(NOW.plus(Duration.ofDays(5)), 200)).thenReturn(List.of(slot.id()));
            when(slots.lockAvailable(slot.id())).thenReturn(Optional.of(slot));
            unitIs(HealthUnitType.SPECIALIZED, true, Set.of("3550308"));
            when(queue.lockNextEligible(SPECIALTY, new EligibilityCriteria(Set.of("3550308")))).thenReturn(Optional.of(candidate));

            var result = service().run();

            assertThat(result.examinedSlots()).isEqualTo(1);
            assertThat(result.allocated()).isEqualTo(1);
            assertThat(slot.status()).isEqualTo(SlotStatus.ALLOCATED);
            verify(queue).markScheduled(candidate.referralId());
            verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentScheduled s
                    && s.referralId().equals(candidate.referralId())
                    && s.queueEnteredAt().equals(candidate.queueEnteredAt())
                    && s.confirmationDeadline().equals(Instant.parse("2026-10-03T02:59:00Z"))));
        }

        @Test
        @DisplayName("vaga sem candidato continua disponível; vaga travada por outra instância é ignorada; falha isolada")
        void shouldCountOutcomesAndIsolateFailures() {
            Slot free = anAvailableSlot();
            UUID lockedElsewhere = UUID.randomUUID();
            UUID broken = UUID.randomUUID();
            when(slots.findAllocatableIds(any(), anyInt())).thenReturn(List.of(free.id(), lockedElsewhere, broken));
            when(slots.lockAvailable(free.id())).thenReturn(Optional.of(free));
            when(slots.lockAvailable(lockedElsewhere)).thenReturn(Optional.empty());
            when(slots.lockAvailable(broken)).thenThrow(new IllegalStateException("conexão perdida"));
            unitIs(HealthUnitType.SPECIALIZED, true, Set.of());
            when(queue.lockNextEligible(any(), any())).thenReturn(Optional.empty());

            var result = service().run();

            assertThat(result.examinedSlots()).isEqualTo(1);
            assertThat(result.allocated()).isZero();
            assertThat(result.withoutCandidate()).isEqualTo(1);
            assertThat(free.status()).isEqualTo(SlotStatus.AVAILABLE);
        }

        private static int anyInt() {
            return org.mockito.ArgumentMatchers.anyInt();
        }
    }

    @Nested
    class Attendance {

        private AppointmentService service(java.time.Clock clock) {
            return new AppointmentService(appointments, slots, patients, queue, audit, events, clock);
        }

        private Appointment withSlot() {
            Appointment appointment = aPendingAppointment();
            Slot slot = Slot.restore(appointment.slotId(), UNIT, SPECIALTY, "Dra. Ana", appointment.startAt(), 30,
                    SlotStatus.ALLOCATED, 0, NOW, NOW, 0L);
            lenient().when(slots.findById(appointment.slotId())).thenReturn(Optional.of(slot));
            when(appointments.findById(appointment.id())).thenReturn(Optional.of(appointment));
            return appointment;
        }

        @Test
        @DisplayName("RF-22: check-in no dia ⇒ ATTENDED, vaga USED, encaminhamento concluído, evento e auditoria")
        void shouldCheckIn() {
            Appointment appointment = withSlot();

            Appointment attended = service(at(appointment.startAt())).checkIn(appointment.id(), SCHEDULER, "ip");

            assertThat(attended.status()).isEqualTo(AppointmentStatus.ATTENDED);
            verify(queue).markCompleted(appointment.referralId());
            verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentAttended));
            verify(audit).record(SCHEDULER, SchedulingAudit.APPOINTMENT_ATTENDED, SchedulingAudit.APPOINTMENT,
                    appointment.id(), "ip", Map.of());
        }

        @Test
        @DisplayName("RN-14: falta ⇒ NO_SHOW, vaga MISSED e encaminhamento para reavaliação")
        void shouldMarkNoShow() {
            Appointment appointment = withSlot();

            service(at(appointment.startAt().plusSeconds(60))).markNoShow(appointment.id(), SCHEDULER, null);

            verify(queue).sendToReview(appointment.referralId(), ReviewReason.NO_SHOW);
            verify(events).publishEvent(argThat((Object e) -> e instanceof AppointmentMissed));
        }

        @Test
        @DisplayName("agendamento de outra unidade ⇒ 403; inexistente ⇒ 404; leitura auditada")
        void shouldScopeAndAuditReads() {
            Appointment appointment = withSlot();

            assertThat(service(CLOCK).get(appointment.id(), ADMIN, "ip")).isSameAs(appointment);
            verify(audit).record(ADMIN, SchedulingAudit.APPOINTMENT_READ, SchedulingAudit.APPOINTMENT, appointment.id(),
                    "ip", Map.of());
            assertThatThrownBy(() -> service(CLOCK).checkIn(appointment.id(), OTHER_SCHEDULER, null))
                    .isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> service(CLOCK).get(UUID.randomUUID(), ADMIN, null))
                    .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("APPOINTMENT_NOT_FOUND");
        }

        @Test
        @DisplayName("listagem: SCHEDULER restrito à unidade; paciente em lote por primeiro nome e CNS mascarado")
        void shouldListWithPatientSummaries() {
            Appointment appointment = aPendingAppointment();
            when(appointments.search(any(), any())).thenReturn(new PageImpl<>(List.of(appointment)));
            when(patients.findSummaries(List.of(appointment.patientId()))).thenReturn(Map.of(appointment.patientId(),
                    new PatientSummary(appointment.patientId(), "Maria", "***********1234", LocalDate.of(1950, 1, 1),
                            "3550308", "+5511999990001", ContactChannel.SMS, false, true, true)));

            var page = service(CLOCK).list(new AppointmentFilter(null, LocalDate.of(2026, 10, 5), null),
                    PageRequest.of(0, 20), SCHEDULER);

            assertThat(page.getContent().getFirst().patientFirstName()).isEqualTo("Maria");
            verify(appointments).search(new AppointmentFilter(UNIT, LocalDate.of(2026, 10, 5), null), PageRequest.of(0, 20));
        }
    }

    @Test
    @DisplayName("SchedulingApi expõe a visão do agendamento; políticas montadas das propriedades (D-3, 5 dias, 2 h)")
    void shouldExposeViewAndPolicies() {
        Appointment appointment = aPendingAppointment();
        when(appointments.findById(appointment.id())).thenReturn(Optional.of(appointment));

        assertThat(new SchedulingApiImpl(appointments, queue, null, events, CLOCK).findAppointmentView(appointment.id()))
                .hasValueSatisfying(view -> assertThat(view.status()).isEqualTo(AppointmentStatus.PENDING_CONFIRMATION));
        var policies = new SchedulingPolicies();
        assertThat(policies.slotLeadTimes(PROPERTIES).regularAllocation()).isEqualTo(Duration.ofDays(5));
        assertThat(policies.confirmationPolicy(PROPERTIES, CLOCK).deadlineFor(appointment.startAt()))
                .isEqualTo(appointment.confirmationDeadline());
    }
}
