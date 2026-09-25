package br.com.vagaviva.identity.application.port.out;

import java.time.Duration;

public record IssuedToken(String value, Duration expiresIn) {
}
