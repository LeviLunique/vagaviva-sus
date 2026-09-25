package br.com.vagaviva.identity.application.port.in;

import br.com.vagaviva.identity.application.port.out.IssuedToken;
import br.com.vagaviva.identity.domain.StaffUser;
import org.jspecify.annotations.Nullable;

/** RF-01: login com e-mail e senha, com bloqueio por tentativas (RN-01). */
public interface AuthenticateUseCase {

    /** @throws br.com.vagaviva.shared.domain.UnauthenticatedException genérica em qualquer falha */
    AuthenticationResult authenticate(LoginCommand command);

    record LoginCommand(String email, String password, @Nullable String clientIp) {
    }

    record AuthenticationResult(IssuedToken token, StaffUser user) {
    }
}
