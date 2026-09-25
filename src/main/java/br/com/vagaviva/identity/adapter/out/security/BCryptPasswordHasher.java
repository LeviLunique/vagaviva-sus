package br.com.vagaviva.identity.adapter.out.security;

import br.com.vagaviva.identity.application.port.out.PasswordHasher;
import br.com.vagaviva.identity.domain.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** RN-02: BCrypt com custo 12 (~250 ms por verificação — encarece ataques de força bruta). */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    static final int STRENGTH = 12;

    private final BCryptPasswordEncoder encoder;

    BCryptPasswordHasher() {
        this(STRENGTH);
    }

    BCryptPasswordHasher(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /** Senhas acima do limite do BCrypt nunca foram aceitas no cadastro: não conferem. */
    @Override
    public boolean matches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }
}
