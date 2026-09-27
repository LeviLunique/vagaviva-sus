package br.com.vagaviva.shared.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/** Leitura (PKCS#8) e geração de chaves RSA para assinar os tokens. */
final class RsaKeys {

    /** Armadura PEM ({@code -----BEGIN/END PRIVATE KEY-----}) removida antes de decodificar o corpo. */
    private static final Pattern PEM_ARMOR = Pattern.compile("-----(BEGIN|END) PRIVATE KEY-----");
    private static final String PEM_PREFIX = "-----";

    private RsaKeys() {
    }

    /**
     * Aceita o PEM PKCS#8 puro, o Base64 desse PEM (formato de {@code JWT_PRIVATE_KEY}, uma linha
     * só — cabe em variável de ambiente) ou o Base64 do DER. A chave pública é derivada da privada.
     */
    static RSAKey fromPkcs8(String value) {
        try {
            String text = value.strip();
            if (!text.startsWith(PEM_PREFIX)) {
                byte[] decoded = Base64.getMimeDecoder().decode(text);
                String asText = new String(decoded, StandardCharsets.US_ASCII).strip();
                if (!asText.startsWith(PEM_PREFIX)) {
                    return toRsaKey(decoded);
                }
                text = asText;
            }
            String body = PEM_ARMOR.matcher(text).replaceAll("").replaceAll("\\s", "");
            return toRsaKey(Base64.getDecoder().decode(body));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException ex) {
            throw new IllegalStateException("JWT_PRIVATE_KEY inválida: esperado RSA PKCS#8 (PEM ou Base64).", ex);
        }
    }

    static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return toRsaKey(generator.generateKeyPair().getPrivate().getEncoded());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Não foi possível gerar o par de chaves RSA.", ex);
        }
    }

    private static RSAKey toRsaKey(byte[] pkcs8) throws GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        var publicKey = (RSAPublicKey) factory.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        try {
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
        } catch (JOSEException ex) {
            throw new GeneralSecurityException(ex);
        }
    }
}
