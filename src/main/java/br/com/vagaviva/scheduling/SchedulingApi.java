package br.com.vagaviva.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública da agenda (Facade), usada pelo engajamento (ações do paciente e lembretes) e, na F6,
 * pelo encaixe. As ações do paciente são idempotentes: repetir a mesma ação devolve o estado atual.
 */
public interface SchedulingApi {

    Optional<AppointmentView> findAppointmentView(UUID appointmentId);

    /**
     * RN-12: confirma até o prazo.
     *
     * @throws br.com.vagaviva.shared.domain.BusinessRuleException {@code CONFIRMATION_DEADLINE_PASSED}
     * @throws br.com.vagaviva.shared.domain.ConflictException {@code APPOINTMENT_INVALID_STATE}
     */
    AppointmentView confirm(UUID appointmentId);

    /** RN-12/RF-26: "não posso ir" até o início ⇒ volta à fila na mesma posição e a vaga é liberada (RN-15). */
    AppointmentView cancelByPatient(UUID appointmentId);

    /** RF-26: "não preciso mais" ⇒ sai da fila e a vaga é liberada (RN-15). */
    AppointmentView withdraw(UUID appointmentId);

    /**
     * Agendamentos para lembrete (RN-11): {@code CONFIRMATION} — aguardando confirmação com prazo em
     * {@code (from, to]}; {@code ATTENDANCE} — confirmados com início em {@code (from, to]}.
     */
    List<AppointmentView> findAppointmentsNeedingReminder(ReminderType type, Instant from, Instant to);

    /** F6: a vaga em oferta de encaixe (unidade, especialidade, início e status atual). */
    Optional<SlotView> findSlotView(UUID slotId);

    /**
     * RN-17/RN-18: toma a vaga em oferta por um {@code UPDATE} condicional — o primeiro aceite vence — e
     * cria o agendamento já confirmado (origem {@code SHORT_NOTICE_OFFER}); o encaminhamento fica agendado.
     *
     * @throws br.com.vagaviva.shared.domain.ConflictException {@code SLOT_ALREADY_FILLED} se a vaga já não
     *     estiver em oferta
     */
    AppointmentView allocateFromOffer(UUID slotId, UUID referralId);

    /** RN-15: encaixe esgotado sem aceite ⇒ vaga perdida ({@code SlotLost}). Ignora vaga que já saiu da oferta. */
    void markSlotLost(UUID slotId);

    enum ReminderType {
        CONFIRMATION,
        ATTENDANCE
    }
}
