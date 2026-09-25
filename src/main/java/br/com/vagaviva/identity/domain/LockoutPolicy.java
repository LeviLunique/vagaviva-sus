package br.com.vagaviva.identity.domain;

import java.time.Duration;

/** RN-01: após {@code maxAttempts} falhas seguidas, o login fica bloqueado por {@code lockDuration}. */
public record LockoutPolicy(int maxAttempts, Duration lockDuration) {

    public LockoutPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts deve ser >= 1");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration deve ser positiva");
        }
    }
}
