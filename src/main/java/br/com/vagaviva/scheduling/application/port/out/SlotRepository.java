package br.com.vagaviva.scheduling.application.port.out;

import br.com.vagaviva.scheduling.application.port.in.SlotUseCases.SlotFilter;
import br.com.vagaviva.scheduling.domain.Slot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SlotRepository {

    /** @throws br.com.vagaviva.shared.domain.BusinessRuleException {@code SLOT_OVERLAP} (índice único) */
    List<Slot> saveAll(List<Slot> slots);

    Slot save(Slot slot);

    Optional<Slot> findById(UUID id);

    /** Há vaga não cancelada do mesmo profissional sobrepondo o intervalo? */
    boolean existsOverlap(UUID unitId, String professionalName, Instant start, Instant end);

    /** Ids das vagas {@code AVAILABLE} com início a partir de {@code from}, mais próximas primeiro (sem travar). */
    List<UUID> findAllocatableIds(Instant from, int limit);

    /** Trava a vaga se ainda estiver {@code AVAILABLE} e livre ({@code FOR UPDATE SKIP LOCKED}). */
    Optional<Slot> lockAvailable(UUID id);

    Page<Slot> search(SlotFilter filter, Pageable pageable);
}
