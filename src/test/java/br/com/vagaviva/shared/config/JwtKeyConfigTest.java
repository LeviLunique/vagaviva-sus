package br.com.vagaviva.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtKeyConfigTest {

    private static byte[] pkcs8;
    private static String pem;

    private final JwtKeyConfig config = new JwtKeyConfig();

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
        // Chave gerada em tempo de execução; a armadura é montada para não haver PEM literal no código.
        pem = armor("BEGIN") + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pkcs8)
                + "\n" + armor("END") + "\n";
    }

    @Test
    @DisplayName("lê a chave no formato de JWT_PRIVATE_KEY (Base64 de um PEM PKCS#8) e deriva a pública")
    void shouldLoadBase64EncodedPem() {
        String value = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));

        RSAKey key = config.jwtSigningKey(properties(value), new MockEnvironment());

        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyID()).isNotBlank();
    }

    @Test
    @DisplayName("também aceita o PEM puro e o Base64 do DER, gerando a mesma chave")
    void shouldAcceptPemAndDer() {
        RSAKey fromPem = config.jwtSigningKey(properties(pem), new MockEnvironment());
        RSAKey fromDer = config.jwtSigningKey(properties(Base64.getEncoder().encodeToString(pkcs8)), new MockEnvironment());

        assertThat(fromPem.getKeyID()).isEqualTo(fromDer.getKeyID());
    }

    @Test
    @DisplayName("chave malformada ⇒ falha no startup com mensagem clara")
    void shouldFailOnInvalidKey() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties("não é uma chave"), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    @DisplayName("no perfil aws a chave é obrigatória (instâncias precisam compartilhar a mesma)")
    void shouldRequireKeyOnAws() {
        var aws = new MockEnvironment();
        aws.setActiveProfiles("aws", "demo");

        assertThatThrownBy(() -> config.jwtSigningKey(properties(" "), aws))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil aws");
    }

    @Test
    @DisplayName("fora da AWS, sem chave, gera um par efêmero")
    void shouldGenerateEphemeralKeyLocally() {
        RSAKey key = config.jwtSigningKey(properties(null), new MockEnvironment());

        assertThat(key.isPrivate()).isTrue();
    }

    @Test
    @DisplayName("token assinado pelo encoder é aceito pelo decoder; emissor diferente é rejeitado")
    void shouldRoundTripAndValidateIssuer() throws Exception {
        RSAKey key = config.jwtSigningKey(properties(pem), new MockEnvironment());
        var encoder = config.jwtEncoder(key);
        var decoder = config.jwtDecoder(key, properties(pem));

        String valid = encode(encoder, "vagaviva");
        String foreign = encode(encoder, "outro-emissor");

        assertThat(decoder.decode(valid).getSubject()).isEqualTo("user-1");
        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode("adulterado.token.x")).isInstanceOf(BadJwtException.class);
    }

    private static String encode(org.springframework.security.oauth2.jwt.JwtEncoder encoder, String issuer) {
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static String armor(String boundary) {
        return "-----" + boundary + " PRIVATE" + " KEY-----";
    }

    private static JwtProperties properties(String privateKey) {
        return new JwtProperties("vagaviva", Duration.ofMinutes(60), privateKey);
    }
}
