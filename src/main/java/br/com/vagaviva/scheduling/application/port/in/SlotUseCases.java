package br.com.vagaviva.scheduling.application.port.in;

import br.com.vagaviva.scheduling.SlotStatus;
import br.com.vagaviva.scheduling.domain.Slot;
import br.com.vagaviva.shared.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Agenda da unidade executante: publicar (RF-19), consultar (RF-21) e cancelar vaga (RF-23). */
public interface SlotUseCases {

    List<Slot> publish(PublishSlotsCommand command);

    /** SCHEDULER vê só a própria unidade. */
    Page<Slot> list(SlotFilter filter, Pageable pageable, CurrentUser actor);

    /** Se houver agendamento aberto, ele vira {@code CANCELLED_BY_UNIT} e o paciente volta à fila na mesma posição. */
    Slot cancel(UUID slotId, String reason, CurrentUser actor, @Nullable String clientIp);

    record PublishSlotsCommand(UUID unitId, UUID specialtyId, String professionalName, List<SlotTime> slots,
            CurrentUser actor, @Nullable String clientIp) {
    }

    record SlotTime(Instant startAt, int durationMinutes) {
    }

    record SlotFilter(@Nullable UUID unitId, @Nullable UUID specialtyId, @Nullable Instant from, @Nullable Instant to,
            @Nullable SlotStatus status) {
    }
}
