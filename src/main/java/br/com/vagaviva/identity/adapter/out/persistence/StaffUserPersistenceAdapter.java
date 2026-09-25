package br.com.vagaviva.identity.adapter.out.persistence;

import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase.StaffUserFilter;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.shared.security.Role;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class StaffUserPersistenceAdapter implements StaffUserRepository {

    private static final String EMAIL_INDEX = "ux_staff_user_email";
    private static final Sort BY_NAME = Sort.by("name", "id");

    private final StaffUserJpaRepository repository;

    StaffUserPersistenceAdapter(StaffUserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public StaffUser save(StaffUser user) {
        try {
            return repository.saveAndFlush(StaffUserEntity.from(user)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre dois cadastros com o mesmo e-mail: o índice único decide.
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(EMAIL_INDEX)) {
                throw new ConflictException("EMAIL_ALREADY_REGISTERED", "Já existe um profissional com este e-mail.");
            }
            throw ex;
        }
    }

    @Override
    public Optional<StaffUser> findById(UUID id) {
        return repository.findById(id).map(StaffUserEntity::toDomain);
    }

    @Override
    public Optional<StaffUser> findByEmail(String email) {
        return repository.findByEmail(email.strip()).map(StaffUserEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email.strip());
    }

    @Override
    public boolean existsByRole(Role role) {
        return repository.existsByRole(role);
    }

    @Override
    public Page<StaffUser> search(StaffUserFilter filter, Pageable pageable) {
        Pageable byName = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_NAME);
        return repository.findAll(matching(filter), byName).map(StaffUserEntity::toDomain);
    }

    private static Specification<StaffUserEntity> matching(StaffUserFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.role() != null) {
                predicates.add(builder.equal(root.get("role"), filter.role()));
            }
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
