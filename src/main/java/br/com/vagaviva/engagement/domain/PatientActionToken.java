package br.com.vagaviva.engagement.domain;

import br.com.vagaviva.shared.domain.Ids;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * RF-25: credencial do link do paciente. O token tem 128 bits aleatórios (16 bytes de
 * {@link SecureRandom}, 22 caracteres em Base64URL) e só o SHA-256 dele é guardado — quem lê o banco
 * não consegue montar o link. Vale até o início do atendimento (ou o fim da oferta, na F6).
 */
public final class PatientActionToken {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 16;

    private final UUID id;
    private final String tokenHash;
    private final TokenPurpose purpose;
    private final UUID subjectId;
    private final Instant expiresAt;
    private final Instant createdAt;
    private @Nullable Instant lastUsedAt;

    private PatientActionToken(UUID id, String tokenHash, TokenPurpose purpose, UUID subjectId, Instant expiresAt,
            Instant createdAt, @Nullable Instant lastUsedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUsedAt = lastUsedAt;
    }

    /** Gera um token novo; o valor em claro só existe na resposta (vai para o link da mensagem). */
    public static Issued issue(TokenPurpose purpose, UUID subjectId, Instant expiresAt, Clock clock) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token = new PatientActionToken(Ids.newId(), hash(raw), purpose, subjectId, expiresAt, clock.instant(), null);
        return new Issued(token, raw);
    }

    public static PatientActionToken restore(UUID id, String tokenHash, TokenPurpose purpose, UUID subjectId,
            Instant expiresAt, Instant createdAt, @Nullable Instant lastUsedAt) {
        return new PatientActionToken(id, tokenHash, purpose, subjectId, expiresAt, createdAt, lastUsedAt);
    }

    /** SHA-256 em hexadecimal minúsculo (64 caracteres). */
    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    public boolean isExpired(Clock clock) {
        return !clock.instant().isBefore(expiresAt);
    }

    public void markUsed(Clock clock) {
        lastUsedAt = clock.instant();
    }

    public UUID id() {
        return id;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public TokenPurpose purpose() {
        return purpose;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable Instant lastUsedAt() {
        return lastUsedAt;
    }

    /** Token recém-emitido: a entidade (só com o hash) e o valor em claro para o link. */
    public record Issued(PatientActionToken token, String raw) {
    }
}
