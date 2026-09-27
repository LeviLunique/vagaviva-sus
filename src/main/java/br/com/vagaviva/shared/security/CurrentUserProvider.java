package br.com.vagaviva.shared.security;

import br.com.vagaviva.shared.domain.UnauthenticatedException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Fornece o {@link CurrentUser} da requisição a partir do JWT validado pelo resource server. */
@Component
public class CurrentUserProvider {

    public CurrentUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return fromJwt(token.getToken());
        }
        throw new UnauthenticatedException("UNAUTHENTICATED", "Autenticação necessária.");
    }

    static CurrentUser fromJwt(Jwt jwt) {
        String unitId = jwt.getClaimAsString(JwtClaims.UNIT_ID);
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                Role.valueOf(jwt.getClaimAsString(JwtClaims.ROLE)),
                unitId == null ? null : UUID.fromString(unitId),
                jwt.getClaimAsString(JwtClaims.NAME));
    }
}
