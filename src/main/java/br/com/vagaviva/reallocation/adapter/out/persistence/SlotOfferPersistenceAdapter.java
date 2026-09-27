package br.com.vagaviva.reallocation.adapter.out.persistence;

import br.com.vagaviva.reallocation.OfferStatus;
import br.com.vagaviva.reallocation.application.port.in.SlotOfferQueryUseCase.OfferFilter;
import br.com.vagaviva.reallocation.application.port.out.SlotOfferRepository;
import br.com.vagaviva.reallocation.domain.SlotOffer;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class SlotOfferPersistenceAdapter implements SlotOfferRepository {

    private static final Sort BY_ROUND = Sort.by("round", "createdAt");

    private final SlotOfferJpaRepository repository;

    SlotOfferPersistenceAdapter(SlotOfferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SlotOffer save(SlotOffer offer) {
        return repository.saveAndFlush(SlotOfferEntity.from(offer)).toDomain();
    }

    @Override
    public Optional<SlotOffer> findById(UUID id) {
        return repository.findById(id).map(SlotOfferEntity::toDomain);
    }

    @Override
    public boolean existsPending(UUID slotId) {
        return repository.existsBySlotIdAndStatus(slotId, OfferStatus.PENDING);
    }

    @Override
    public int findLastRound(UUID slotId) {
        return repository.findLastRound(slotId);
    }

    @Override
    public Set<UUID> findPatientsToExclude(UUID slotId) {
        return new HashSet<>(repository.findPatientsToExclude(slotId));
    }

    @Override
    public int supersedeOthers(UUID slotId, UUID acceptedOfferId, Instant now) {
        return repository.supersedeOthers(slotId, acceptedOfferId, now);
    }

    @Override
    public List<UUID> findSlotsWithOverdueOffers(Instant now, int limit) {
        return repository.findSlotsWithOverdueOffers(now, PageRequest.of(0, limit));
    }

    @Override
    public int expireOverdue(UUID slotId, Instant now) {
        return repository.expireOverdue(slotId, now);
    }

    @Override
    public Page<SlotOffer> search(OfferFilter filter, Pageable pageable) {
        Pageable byRound = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_ROUND);
        Specification<SlotOfferEntity> matching = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.slotId() != null) {
                predicates.add(builder.equal(root.get("slotId"), filter.slotId()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(matching, byRound).map(SlotOfferEntity::toDomain);
    }
}
