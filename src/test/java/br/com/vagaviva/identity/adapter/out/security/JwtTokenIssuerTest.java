package br.com.vagaviva.identity.adapter.out.security;

import static br.com.vagaviva.identity.fixtures.StaffUserFixture.aStaffUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.identity.application.port.out.IssuedToken;
import br.com.vagaviva.identity.domain.StaffUser;
import br.com.vagaviva.shared.config.JwtKeyConfig;
import br.com.vagaviva.shared.config.JwtProperties;
import br.com.vagaviva.shared.security.Role;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtTokenIssuerTest {

    private static final JwtProperties PROPERTIES = new JwtProperties("vagaviva", Duration.ofMinutes(60), null);

    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        var config = new JwtKeyConfig();
        RSAKey key = config.jwtSigningKey(PROPERTIES, new MockEnvironment());
        encoder = config.jwtEncoder(key);
        decoder = config.jwtDecoder(key, PROPERTIES);
    }

    @Test
    @DisplayName("emite JWT RS256 verificável pela chave pública com sub, papel, unidade, nome e exp de 60 min")
    void shouldIssueVerifiableTokenWithClaims() {
        StaffUser user = aStaffUser().withRole(Role.SCHEDULER).build();
        var issuer = new JwtTokenIssuer(encoder, PROPERTIES, Clock.systemUTC());

        IssuedToken token = issuer.issue(user);
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("vagaviva");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("SCHEDULER");
        assertThat(jwt.getClaimAsString("unit_id")).isEqualTo(user.healthUnitId().toString());
        assertThat(jwt.getClaimAsString("name")).isEqualTo(user.name());
        assertThat(jwt.getClaims()).doesNotContainKey("email");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(token.expiresIn()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("usuário sem unidade não recebe a claim unit_id")
    void shouldOmitUnitClaimWhenUserHasNoUnit() {
        var issuer = new JwtTokenIssuer(encoder, PROPERTIES, Clock.systemUTC());

        Jwt jwt = decoder.decode(issuer.issue(aStaffUser().withRole(Role.REGULATOR).build()).value());

        assertThat(jwt.getClaims()).doesNotContainKey("unit_id");
    }

    @Test
    @DisplayName("token expirado é rejeitado na validação")
    void shouldRejectExpiredToken() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        var issuer = new JwtTokenIssuer(encoder, PROPERTIES, twoHoursAgo);

        String token = issuer.issue(aStaffUser().build()).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }
}
