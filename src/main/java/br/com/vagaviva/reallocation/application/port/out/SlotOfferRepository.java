package br.com.vagaviva.reallocation.application.port.out;

import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase.OfferFilter;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SlotOfferRepository {

    SlotOffer save(SlotOffer offer);

    Optional<SlotOffer> findById(UUID id);

    boolean existsPending(UUID slotId);

    /** Maior rodada já aberta para a vaga (0 se nenhuma). */
    int findLastRound(UUID slotId);

    /** Pacientes que já receberam oferta desta vaga (qualquer status) ou têm oferta pendente de outra vaga (RN-16). */
    Set<UUID> findPatientsToExclude(UUID slotId);

    /** Vencedor escolhido: as demais pendentes da vaga viram {@code SUPERSEDED}. */
    int supersedeOthers(UUID slotId, UUID acceptedOfferId, Instant now);

    /** Vagas com oferta pendente vencida (sem travar), para o job. */
    List<UUID> findSlotsWithOverdueOffers(Instant now, int limit);

    /** Expira as pendentes vencidas da vaga. */
    int expireOverdue(UUID slotId, Instant now);

    Page<SlotOffer> search(OfferFilter filter, Pageable pageable);
}
