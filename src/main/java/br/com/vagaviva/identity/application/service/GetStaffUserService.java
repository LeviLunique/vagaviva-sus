package br.com.vagaviva.identity.application.service;

import br.com.vagaviva.identity.application.port.in.GetStaffUserUseCase;
import br.com.vagaviva.identity.application.port.out.StaffUserRepository;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetStaffUserService implements GetStaffUserUseCase {

    private final StaffUserRepository repository;

    GetStaffUserService(StaffUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffUser get(UUID userId) {
        return repository.findById(userId).orElseThrow(StaffUserNotFound::new);
    }

    static final class StaffUserNotFound extends NotFoundException {
        StaffUserNotFound() {
            super("STAFF_USER_NOT_FOUND", "Profissional não encontrado.");
        }
    }
}
