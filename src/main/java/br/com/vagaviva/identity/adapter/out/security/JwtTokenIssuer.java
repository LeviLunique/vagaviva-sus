package br.com.vagaviva.identity.adapter.out.security;

import br.com.vagaviva.identity.application.port.out.IssuedToken;
import br.com.vagaviva.identity.application.port.out.TokenIssuer;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.config.JwtProperties;
import br.com.vagaviva.shared.security.JwtClaims;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite o JWT RS256 do profissional: {@code sub} = id, papel, unidade e nome — sem e-mail nem
 * outros dados pessoais (minimização, LGPD).
 */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(StaffUser user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(JwtClaims.ROLE, user.role().name())
                .claim(JwtClaims.NAME, user.name());
        if (user.healthUnitId() != null) {
            claims.claim(JwtClaims.UNIT_ID, user.healthUnitId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, properties.ttl());
    }
}
