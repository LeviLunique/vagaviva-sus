package br.com.vagaviva.identity.application.port.in;

import br.com.vagaviva.identity.domain.StaffUser;
import java.util.UUID;

/** RF-04 ({@code /auth/me}) e consulta de um profissional pelo ADMIN. */
public interface GetStaffUserUseCase {

    StaffUser get(UUID userId);
}
