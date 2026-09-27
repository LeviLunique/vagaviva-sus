package br.com.vagaviva.identity.application.port.out;

import br.com.vagaviva.identity.domain.StaffUser;

public interface TokenIssuer {

    IssuedToken issue(StaffUser user);
}
