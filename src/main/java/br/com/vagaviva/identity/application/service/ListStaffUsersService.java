package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.application.port.in.ListStaffUsersUseCase;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListStaffUsersService implements ListStaffUsersUseCase {

    private final StaffUserRepository repository;

    ListStaffUsersService(StaffUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StaffUser> list(StaffUserFilter filter, Pageable pageable) {
        return repository.search(filter, pageable);
    }
}
